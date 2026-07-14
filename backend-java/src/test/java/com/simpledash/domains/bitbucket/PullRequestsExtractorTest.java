package com.simpledash.domains.bitbucket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PullRequestsExtractorTest {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedQuery = new AtomicReference<>();
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private volatile String responseBody = "{\"values\":[]}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rest/api/1.0/projects/PROJ/repos/repo/pull-requests", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
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

    private static Resource repoResource() {
        return new Resource("mon-repo", "mon-repo", List.of("bitbucket"), Map.of("project", "PROJ", "repo", "repo"));
    }

    @Test
    void mapsPullRequestsToTableWidgetWithMineAndApprovedFlags() throws Exception {
        long createdDate1 = 1752144000000L;
        long createdDate2 = 1752057600000L;
        responseBody = """
            {"values":[
              {"id":42,"title":"Add feature X","createdDate":%d,
               "author":{"user":{"slug":"jdoe","displayName":"John Doe"}},
               "reviewers":[{"user":{"slug":"asmith","displayName":"Alice Smith"},"approved":true}],
               "links":{"self":[{"href":"https://bitbucket.example.local/projects/PROJ/repos/repo/pull-requests/42"}]}},
              {"id":43,"title":"Fix bug Y","createdDate":%d,
               "author":{"user":{"slug":"asmith","displayName":"Alice Smith"}},
               "reviewers":[{"user":{"slug":"jdoe","displayName":"John Doe"},"approved":false}],
               "links":{"self":[{"href":"https://bitbucket.example.local/projects/PROJ/repos/repo/pull-requests/43"}]}}
            ]}
            """.formatted(createdDate1, createdDate2);

        var extractor = new PullRequestsExtractor(baseUrl, "fake-token", "jdoe");

        List<ExtractorWidget> widgets = extractor.fetch(repoResource());

        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals(List.of("Titre", "Auteur", "À moi", "Validée", "Date"), widget.table().columns());
        assertEquals(2, widget.table().rows().size());

        assertEquals(
            List.of("Add feature X", "John Doe", "Oui", "Non", DISPLAY_DATE_FORMAT.format(Instant.ofEpochMilli(createdDate1))),
            widget.table().rows().get(0).cells()
        );
        assertEquals(
            "https://bitbucket.example.local/projects/PROJ/repos/repo/pull-requests/42",
            widget.table().rows().get(0).url()
        );

        assertEquals(
            List.of("Fix bug Y", "Alice Smith", "Non", "Non", DISPLAY_DATE_FORMAT.format(Instant.ofEpochMilli(createdDate2))),
            widget.table().rows().get(1).cells()
        );

        assertEquals("Bearer fake-token", capturedAuth.get());
        assertEquals("/rest/api/1.0/projects/PROJ/repos/repo/pull-requests", capturedPath.get());
        assertEquals("state=OPEN", capturedQuery.get());
    }

    @Test
    void approvedIsYesWhenIAmReviewerAndApproved() throws Exception {
        responseBody = """
            {"values":[
              {"id":1,"title":"PR","createdDate":1752144000000,
               "author":{"user":{"slug":"someone-else","displayName":"Someone"}},
               "reviewers":[{"user":{"slug":"jdoe","displayName":"John Doe"},"approved":true}],
               "links":{"self":[{"href":"https://bitbucket.example.local/x"}]}}
            ]}
            """;
        var extractor = new PullRequestsExtractor(baseUrl, "fake-token", "jdoe");

        List<ExtractorWidget> widgets = extractor.fetch(repoResource());

        List<String> cells = widgets.get(0).table().rows().get(0).cells();
        assertEquals("Oui", cells.get(3));
    }

    @Test
    void fallsBackToDataWidgetWhenNoOpenPullRequests() throws Exception {
        responseBody = "{\"values\":[]}";
        var extractor = new PullRequestsExtractor(baseUrl, "fake-token", "jdoe");

        List<ExtractorWidget> widgets = extractor.fetch(repoResource());

        assertEquals(1, widgets.size());
        assertNull(widgets.get(0).table());
        assertEquals(Map.of("Pull requests", "aucune PR ouverte"), widgets.get(0).data());
    }

    @Test
    void throwsExplicitErrorOnHttpFailure() throws Exception {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rest/api/1.0/projects/PROJ/repos/repo/pull-requests", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        var extractor = new PullRequestsExtractor(baseUrl, "fake-token", "jdoe");

        Exception ex = assertThrows(RuntimeException.class, () -> extractor.fetch(repoResource()));
        assertTrue(ex.getMessage().contains("403"));
    }
}
