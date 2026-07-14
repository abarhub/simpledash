package com.simpledash.domains.bitbucket;

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PullRequestsExtractor implements Extractor {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;
    private final String username;

    public PullRequestsExtractor() {
        this(BitbucketConfig.BASE_URL, BitbucketConfig.TOKEN, BitbucketConfig.USERNAME);
    }

    PullRequestsExtractor(String baseUrl, String token, String username) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.username = username;
    }

    public String id() {
        return "bitbucket-prs";
    }

    public String name() {
        return "Pull requests";
    }

    public String description() {
        return "Liste des PR ouvertes du dépôt — un tableau (titre, auteur, à moi, validée, date)";
    }

    public List<String> compatibleTypes() {
        return List.of("bitbucket");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        String project = (String) resource.get("project");
        String repo = (String) resource.get("repo");
        String root = stripTrailingSlash(baseUrl);

        URI uri = URI.create(
            root + "/rest/api/1.0/projects/" + project + "/repos/" + repo + "/pull-requests?state=OPEN"
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Erreur Bitbucket: " + response.statusCode());
        }

        JsonNode json = MAPPER.readTree(response.body());
        List<WidgetRow> rows = new ArrayList<>();
        for (JsonNode pr : json.path("values")) {
            JsonNode authorUser = pr.path("author").path("user");
            String authorSlug = authorUser.path("slug").asText(null);
            String authorName = authorUser.path("displayName").asText("?");
            boolean isMine = username != null && username.equals(authorSlug);
            boolean approvedByMe = isApprovedByMe(pr.path("reviewers"));

            JsonNode createdDate = pr.path("createdDate");
            String date = createdDate.isMissingNode() || createdDate.isNull()
                ? "?"
                : DISPLAY_DATE_FORMAT.format(Instant.ofEpochMilli(createdDate.asLong()));

            String url = pr.path("links").path("self").path(0).path("href").asText(null);

            rows.add(new WidgetRow(url, List.of(
                pr.path("title").asText(),
                authorName,
                isMine ? "Oui" : "Non",
                approvedByMe ? "Oui" : "Non",
                date
            )));
        }

        if (rows.isEmpty()) {
            return List.of(ExtractorWidget.data(
                "pull-requests", "Pull requests", Map.of("Pull requests", "aucune PR ouverte")
            ));
        }

        return List.of(ExtractorWidget.table(
            "pull-requests", "Pull requests",
            new WidgetTable(List.of("Titre", "Auteur", "À moi", "Validée", "Date"), rows)
        ));
    }

    private boolean isApprovedByMe(JsonNode reviewers) {
        for (JsonNode reviewer : reviewers) {
            String slug = reviewer.path("user").path("slug").asText(null);
            boolean approved = reviewer.path("approved").asBoolean(false);
            if (username != null && username.equals(slug) && approved) {
                return true;
            }
        }
        return false;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
