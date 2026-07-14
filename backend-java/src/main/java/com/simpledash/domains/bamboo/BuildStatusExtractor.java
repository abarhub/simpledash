package com.simpledash.domains.bamboo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.domains.WidgetRow;
import com.simpledash.domains.WidgetTable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BuildStatusExtractor implements Extractor {

    private static final Map<String, String> STATE_LABELS = Map.of("Successful", "OK", "Failed", "Erreur");
    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;

    public BuildStatusExtractor() {
        this(BambooConfig.BASE_URL, BambooConfig.TOKEN);
    }

    BuildStatusExtractor(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public String id() {
        return "bamboo-build-status";
    }

    public String name() {
        return "Builds";
    }

    public String description() {
        return "Dernier build du plan et de ses branches (limité) — statut, résultat, date, commit";
    }

    public List<String> compatibleTypes() {
        return List.of("bamboo");
    }

    // fetchLatestResult/fetchBranches puis chaque résultat de branche sont
    // lancés en parallèle via des threads virtuels, équivalent des
    // Promise.all imbriqués côté Node (build-status.js).
    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        String planKey = (String) resource.get("planKey");
        int maxBranches = resource.get("maxBranches") instanceof Integer n ? n : 5;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<JsonNode> mainResultFuture = executor.submit(() -> fetchLatestResult(planKey));
            Future<List<JsonNode>> branchesFuture = executor.submit(() -> fetchBranches(planKey, maxBranches));

            JsonNode mainResult = await(mainResultFuture);
            List<JsonNode> branches = await(branchesFuture);

            List<Future<JsonNode>> branchResultFutures = new ArrayList<>();
            for (JsonNode branch : branches) {
                branchResultFutures.add(executor.submit(() -> fetchLatestResult(branch.path("key").asText())));
            }

            List<WidgetRow> rows = new ArrayList<>();
            rows.add(buildRow(resource.name(), planKey, mainResult));
            for (int i = 0; i < branches.size(); i++) {
                JsonNode branch = branches.get(i);
                rows.add(buildRow(
                    branch.path("shortName").asText(),
                    branch.path("key").asText(),
                    await(branchResultFutures.get(i))
                ));
            }

            return List.of(ExtractorWidget.table(
                "builds", "Builds",
                new WidgetTable(List.of("Branche", "Statut", "Résultat", "Date", "Commit"), rows)
            ));
        }
    }

    // Future.get() enveloppe toute exception du Callable dans une
    // ExecutionException (checked) — on la déballe pour propager le
    // "Erreur Bamboo: ..." tel quel, plutôt que masqué dans son message.
    private static <T> T await(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException e) {
            switch (e.getCause()) {
                case RuntimeException re -> throw re;
                case Exception ex -> throw ex;
                case null, default -> throw e;
            }
        }
    }

    private JsonNode fetchLatestResult(String planKey) throws Exception {
        URI uri = URI.create(root() + "/rest/api/latest/result/" + planKey + ".json?max-results=1");
        JsonNode result = get(uri).path("results").path("result").path(0);
        return result.isMissingNode() ? null : result;
    }

    private List<JsonNode> fetchBranches(String planKey, int maxBranches) throws Exception {
        URI uri = URI.create(root() + "/rest/api/latest/plan/" + planKey + ".json?expand=branches");
        List<JsonNode> branches = new ArrayList<>();
        for (JsonNode branch : get(uri).path("branches").path("branch")) {
            branches.add(branch);
        }
        return branches.size() > maxBranches ? branches.subList(0, maxBranches) : branches;
    }

    private JsonNode get(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Erreur Bamboo: " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    private WidgetRow buildRow(String name, String planKey, JsonNode result) {
        String lifeCycleState = result == null ? "?" : result.path("lifeCycleState").asText("?");

        String state = "?";
        if (result != null) {
            String rawState = result.path("state").asText(null);
            state = rawState == null ? "?" : STATE_LABELS.getOrDefault(rawState, rawState);
        }

        String date = "?";
        if (result != null) {
            JsonNode started = result.path("buildStartedTime");
            if (!started.isMissingNode() && !started.isNull()) {
                date = OffsetDateTime.parse(started.asText()).format(DISPLAY_DATE_TIME_FORMAT);
            }
        }

        String commit = "?";
        if (result != null) {
            JsonNode revision = result.path("vcsRevisionKey");
            if (!revision.isMissingNode() && !revision.isNull()) {
                String rev = revision.asText();
                commit = rev.length() > 8 ? rev.substring(0, 8) : rev;
            }
        }

        return new WidgetRow(root() + "/browse/" + planKey, List.of(name, lifeCycleState, state, date, commit));
    }

    private String root() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
