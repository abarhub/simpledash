package com.simpledash.domains.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.domains.WidgetRow;
import com.simpledash.domains.WidgetTable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IssuesExtractor implements Extractor {

    // Format Jira ("+0000", sans deux-points) plutôt que ISO_OFFSET_DATE_TIME.
    private static final DateTimeFormatter JIRA_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;

    public IssuesExtractor() {
        this(JiraConfig.BASE_URL, JiraConfig.TOKEN);
    }

    IssuesExtractor(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public String id() {
        return "jira-issues";
    }

    public String name() {
        return "Tickets";
    }

    public String description() {
        return "Résultat de la requête JQL — un tableau (titre, ID, statut, mise à jour)";
    }

    public List<String> compatibleTypes() {
        return List.of("jira");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        String jql = (String) resource.get("jql");
        int maxResults = resource.get("maxResults") instanceof Integer n ? n : 5;
        String root = stripTrailingSlash(baseUrl);

        String query = "jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
            + "&maxResults=" + maxResults
            + "&fields=summary,status,updated";
        URI uri = URI.create(root + "/rest/api/2/search?" + query);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Erreur Jira: " + response.statusCode());
        }

        JsonNode json = MAPPER.readTree(response.body());
        List<WidgetRow> rows = new ArrayList<>();
        for (JsonNode issue : json.path("issues")) {
            String key = issue.path("key").asText();
            JsonNode fields = issue.path("fields");
            String summary = fields.path("summary").asText(key);
            String status = fields.path("status").path("name").asText("?");
            JsonNode updatedNode = fields.path("updated");
            String updated = updatedNode.isMissingNode() || updatedNode.isNull()
                ? "?"
                : OffsetDateTime.parse(updatedNode.asText(), JIRA_DATE_FORMAT).format(DISPLAY_DATE_FORMAT);

            rows.add(new WidgetRow(root + "/browse/" + key, List.of(summary, key, status, updated)));
        }

        if (rows.isEmpty()) {
            return List.of(ExtractorWidget.data("issues", "Tickets", Map.of("Tickets", "aucun ticket trouvé")));
        }

        return List.of(ExtractorWidget.table(
            "issues", "Tickets",
            new WidgetTable(List.of("Titre", "ID", "Statut", "Mise à jour"), rows)
        ));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
