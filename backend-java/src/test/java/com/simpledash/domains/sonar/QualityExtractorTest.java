package com.simpledash.domains.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QualityExtractorTest {

    private static final DateTimeFormatter SONAR_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private HttpServer server;
    private String baseUrl;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = responses.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static Resource projectResource() {
        return new Resource("mon-projet", "Mon projet", List.of("sonar"), Map.of("projectKey", "my-project"));
    }

    @Test
    void mapsMetricsToDataWidgetConvertingRatingsToLetters() throws Exception {
        responses.put("/api/measures/component", """
            {"component":{"measures":[
              {"metric":"reliability_rating","value":"1.0"},
              {"metric":"security_rating","value":"2.0"},
              {"metric":"sqale_rating","value":"3.0"},
              {"metric":"coverage","value":"78.3"},
              {"metric":"duplicated_lines_density","value":"4.2"},
              {"metric":"alert_status","value":"OK"}
            ]}}
            """);
        responses.put("/api/project_analyses/search", """
            {"analyses":[{"date":"2026-07-10T10:00:00+0200","revision":"abcdef1234567890"}]}
            """);

        var extractor = new QualityExtractor(baseUrl, "fake-token");

        List<ExtractorWidget> widgets = extractor.fetch(projectResource());

        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals("Qualité", widget.title());
        assertEquals(baseUrl + "/dashboard?id=my-project", widget.url());
        assertEquals(Map.ofEntries(
            Map.entry("Fiabilité", "A"),
            Map.entry("Sécurité", "B"),
            Map.entry("Maintenabilité", "C"),
            Map.entry("Quality Gate", "OK"),
            Map.entry("Couverture", "78.3%"),
            Map.entry("Duplication", "4.2%"),
            Map.entry("Commit", "abcdef12"),
            Map.entry("Date", OffsetDateTime.parse("2026-07-10T10:00:00+0200", SONAR_DATE_FORMAT).format(DISPLAY_DATE_TIME_FORMAT))
        ), widget.data());

        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("fake-token:".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuth, capturedAuth.get());
    }

    @Test
    void handlesFailedQualityGateAndMissingRevision() throws Exception {
        responses.put("/api/measures/component", """
            {"component":{"measures":[
              {"metric":"reliability_rating","value":"1.0"},
              {"metric":"security_rating","value":"1.0"},
              {"metric":"sqale_rating","value":"1.0"},
              {"metric":"coverage","value":"50.0"},
              {"metric":"duplicated_lines_density","value":"0.0"},
              {"metric":"alert_status","value":"ERROR"}
            ]}}
            """);
        responses.put("/api/project_analyses/search", "{\"analyses\":[{\"date\":\"2026-07-10T10:00:00+0200\"}]}");

        var extractor = new QualityExtractor(baseUrl, "fake-token");

        List<ExtractorWidget> widgets = extractor.fetch(projectResource());

        assertEquals("ERROR", widgets.get(0).data().get("Quality Gate"));
        assertEquals("?", widgets.get(0).data().get("Commit"));
    }

    @Test
    void throwsExplicitErrorOnHttpFailure() throws Exception {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        var extractor = new QualityExtractor(baseUrl, "fake-token");

        Exception ex = assertThrows(RuntimeException.class, () -> extractor.fetch(projectResource()));
        assertTrue(ex.getMessage().contains("403"));
    }
}
