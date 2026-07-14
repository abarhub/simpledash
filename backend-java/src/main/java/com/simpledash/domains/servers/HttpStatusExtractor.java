package com.simpledash.domains.servers;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class HttpStatusExtractor implements Extractor {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public String id() {
        return "http-status";
    }

    public String name() {
        return "Statut HTTP";
    }

    public String description() {
        return "Vérifie que l'URL répond (appel distant)";
    }

    public List<String> compatibleTypes() {
        return List.of("http");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        String url = (String) resource.get("url");

        long start = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        long elapsed = System.currentTimeMillis() - start;

        return List.of(ExtractorWidget.data("status", "Statut", Map.of(
            "URL", url,
            "Statut", String.valueOf(response.statusCode()),
            "Temps de réponse", elapsed + " ms"
        )));
    }
}
