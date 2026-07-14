package com.simpledash.domains.sonar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class QualityExtractor implements Extractor {

    private static final Map<Integer, String> RATING_LETTERS = Map.of(1, "A", 2, "B", 3, "C", 4, "D", 5, "E");

    // Format Sonar ("+0200", sans deux-points, pas de millisecondes) :
    // différent du "+0000.000" de Jira, donc pas d'ISO_OFFSET_DATE_TIME
    // non plus.
    private static final DateTimeFormatter SONAR_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;

    public QualityExtractor() {
        this(SonarConfig.BASE_URL, SonarConfig.TOKEN);
    }

    QualityExtractor(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public String id() {
        return "sonar-quality";
    }

    public String name() {
        return "Qualité";
    }

    public String description() {
        return "Notes (fiabilité/sécurité/maintenabilité), quality gate, couverture, duplication";
    }

    public List<String> compatibleTypes() {
        return List.of("sonar");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        String projectKey = (String) resource.get("projectKey");
        String root = stripTrailingSlash(baseUrl);
        String encodedProjectKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8);

        URI measuresUri = URI.create(root + "/api/measures/component?component=" + encodedProjectKey
            + "&metricKeys=reliability_rating,security_rating,sqale_rating,coverage,duplicated_lines_density,alert_status");
        URI analysesUri = URI.create(root + "/api/project_analyses/search?project=" + encodedProjectKey + "&ps=1");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<JsonNode> measuresFuture = executor.submit(() -> get(measuresUri));
            Future<JsonNode> analysesFuture = executor.submit(() -> get(analysesUri));

            JsonNode measures = await(measuresFuture);
            JsonNode analyses = await(analysesFuture);

            Map<String, String> metrics = new LinkedHashMap<>();
            for (JsonNode measure : measures.path("component").path("measures")) {
                metrics.put(measure.path("metric").asText(), measure.path("value").asText(null));
            }
            JsonNode analysis = analyses.path("analyses").path(0);
            String revision = analysis.path("revision").asText(null);
            String date = analysis.path("date").asText(null);

            Map<String, String> data = new LinkedHashMap<>();
            data.put("Fiabilité", ratingLetter(metrics.get("reliability_rating")));
            data.put("Sécurité", ratingLetter(metrics.get("security_rating")));
            data.put("Maintenabilité", ratingLetter(metrics.get("sqale_rating")));
            data.put("Quality Gate", orUnknown(metrics.get("alert_status")));
            data.put("Couverture", percent(metrics.get("coverage")));
            data.put("Duplication", percent(metrics.get("duplicated_lines_density")));
            data.put("Commit", isSet(revision) ? revision.substring(0, Math.min(8, revision.length())) : "?");
            data.put("Date", isSet(date) ? OffsetDateTime.parse(date, SONAR_DATE_FORMAT).format(DISPLAY_DATE_TIME_FORMAT) : "?");

            String url = root + "/dashboard?id=" + encodedProjectKey;
            return List.of(ExtractorWidget.data("quality", "Qualité", url, data));
        }
    }

    private JsonNode get(URI uri) throws Exception {
        String credentials = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", "Basic " + credentials)
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Erreur SonarQube: " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    private static String ratingLetter(String value) {
        if (!isSet(value)) {
            return "?";
        }
        try {
            int rating = (int) Double.parseDouble(value);
            return RATING_LETTERS.getOrDefault(rating, "?");
        } catch (NumberFormatException e) {
            return "?";
        }
    }

    private static String orUnknown(String value) {
        return isSet(value) ? value : "?";
    }

    private static String percent(String value) {
        return isSet(value) ? value + "%" : "?";
    }

    private static boolean isSet(String value) {
        return value != null && !value.isEmpty();
    }

    // Future.get() enveloppe toute exception du Callable dans une
    // ExecutionException (checked) — on la déballe pour propager le
    // "Erreur SonarQube: ..." tel quel.
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

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
