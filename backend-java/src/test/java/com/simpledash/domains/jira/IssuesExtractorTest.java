package com.simpledash.domains.jira;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssuesExtractorTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedQuery = new AtomicReference<>();
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private volatile String responseBody = "{\"issues\":[]}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rest/api/2/search", exchange -> {
            capturedQuery.set(exchange.getRequestURI().getRawQuery());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsIssuesToTableWidgetAndBuildsExpectedRequest() throws Exception {
        responseBody = """
            {"issues":[
              {"key":"PROJ-123","fields":{"summary":"Corriger le bug de login","status":{"name":"In Progress"},"updated":"2026-07-10T14:32:00.000+0000"}},
              {"key":"PROJ-124","fields":{"summary":"Ameliorer les perfs","status":{"name":"To Do"},"updated":"2026-07-09T09:00:00.000+0000"}}
            ]}
            """;
        var extractor = new IssuesExtractor(baseUrl, "fake-token");
        var resource = new Resource("my-issues", "Mes tickets", List.of("jira"), Map.of(
            "jql", "assignee = currentUser()", "maxResults", 5
        ));

        List<ExtractorWidget> widgets = extractor.fetch(resource);

        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals("Tickets", widget.title());
        assertEquals(List.of("Titre", "ID", "Statut", "Mise à jour"), widget.table().columns());
        assertEquals(2, widget.table().rows().size());
        assertEquals(
            List.of("Corriger le bug de login", "PROJ-123", "In Progress", "10/07/2026"),
            widget.table().rows().get(0).cells()
        );
        assertEquals(baseUrl + "/browse/PROJ-123", widget.table().rows().get(0).url());

        assertEquals("Bearer fake-token", capturedAuth.get());
        String decodedQuery = URLDecoder.decode(capturedQuery.get(), StandardCharsets.UTF_8);
        assertTrue(decodedQuery.contains("jql=assignee = currentUser()"));
        assertTrue(decodedQuery.contains("maxResults=5"));
    }

    @Test
    void defaultsMaxResultsToFiveWhenNotSetOnResource() throws Exception {
        var extractor = new IssuesExtractor(baseUrl, "fake-token");
        var resource = new Resource("my-issues", "Mes tickets", List.of("jira"), Map.of("jql", "foo"));

        extractor.fetch(resource);

        assertTrue(capturedQuery.get().contains("maxResults=5"));
    }

    @Test
    void fallsBackToDataWidgetWhenNoIssuesMatch() throws Exception {
        responseBody = "{\"issues\":[]}";
        var extractor = new IssuesExtractor(baseUrl, "fake-token");
        var resource = new Resource("my-issues", "Mes tickets", List.of("jira"), Map.of("jql", "foo"));

        List<ExtractorWidget> widgets = extractor.fetch(resource);

        assertEquals(1, widgets.size());
        assertNull(widgets.get(0).table());
        assertEquals(Map.of("Tickets", "aucun ticket trouvé"), widgets.get(0).data());
    }

    @Test
    void throwsExplicitErrorOnHttpFailure() throws Exception {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rest/api/2/search", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        var extractor = new IssuesExtractor(baseUrl, "fake-token");
        var resource = new Resource("my-issues", "Mes tickets", List.of("jira"), Map.of("jql", "foo"));

        Exception ex = assertThrows(RuntimeException.class, () -> extractor.fetch(resource));
        assertTrue(ex.getMessage().contains("401"));
    }
}
