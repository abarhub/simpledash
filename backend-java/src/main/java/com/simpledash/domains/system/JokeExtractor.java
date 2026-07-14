package com.simpledash.domains.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class JokeExtractor implements Extractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public String id() {
        return "joke";
    }

    public String name() {
        return "Blague (appel distant)";
    }

    public String description() {
        return "Récupère une blague via une API publique (démo d'appel réseau)";
    }

    public List<String> compatibleTypes() {
        return List.of("local");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://icanhazdadjoke.com/"))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Erreur API: " + response.statusCode());
        }

        JsonNode json = MAPPER.readTree(response.body());
        return List.of(
            ExtractorWidget.data("random", "Blague du moment", Map.of("Blague", json.get("joke").asText()))
        );
    }
}
