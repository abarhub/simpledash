package com.simpledash.domains.bamboo;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BuildStatusExtractorTest {

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

    private static Resource planResource(int maxBranches) {
        return new Resource("mon-plan", "Mon plan", List.of("bamboo"), Map.of("planKey", "PROJ-PLAN", "maxBranches", maxBranches));
    }

    private static Resource planResourceDefaultMaxBranches() {
        return new Resource("mon-plan", "Mon plan", List.of("bamboo"), Map.of("planKey", "PROJ-PLAN"));
    }

    @Test
    void mapsMainPlanAndBranchesToTableWidget() throws Exception {
        responses.put("/rest/api/latest/result/PROJ-PLAN.json", """
            {"results":{"result":[{"state":"Successful","lifeCycleState":"Finished","buildStartedTime":"2026-07-10T10:00:00.000+02:00","vcsRevisionKey":"abcdef1234567890"}]}}
            """);
        responses.put("/rest/api/latest/plan/PROJ-PLAN.json", """
            {"branches":{"branch":[{"key":"PROJ-PLAN-BR1","shortName":"feature-x"},{"key":"PROJ-PLAN-BR2","shortName":"feature-y"}]}}
            """);
        responses.put("/rest/api/latest/result/PROJ-PLAN-BR1.json", """
            {"results":{"result":[{"state":"Failed","lifeCycleState":"Finished","buildStartedTime":"2026-07-09T09:00:00.000+02:00","vcsRevisionKey":"1111111111111111"}]}}
            """);
        responses.put("/rest/api/latest/result/PROJ-PLAN-BR2.json", "{\"results\":{\"result\":[]}}");

        var extractor = new BuildStatusExtractor(baseUrl, "fake-token");

        List<ExtractorWidget> widgets = extractor.fetch(planResource(5));

        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals("Builds", widget.title());
        assertEquals(List.of("Branche", "Statut", "Résultat", "Date", "Commit"), widget.table().columns());
        assertEquals(3, widget.table().rows().size());

        assertEquals(
            List.of("Mon plan", "Finished", "OK",
                OffsetDateTime.parse("2026-07-10T10:00:00.000+02:00").format(DISPLAY_DATE_TIME_FORMAT), "abcdef12"),
            widget.table().rows().get(0).cells()
        );
        assertEquals(baseUrl + "/browse/PROJ-PLAN", widget.table().rows().get(0).url());

        assertEquals(
            List.of("feature-x", "Finished", "Erreur",
                OffsetDateTime.parse("2026-07-09T09:00:00.000+02:00").format(DISPLAY_DATE_TIME_FORMAT), "11111111"),
            widget.table().rows().get(1).cells()
        );
        assertEquals(baseUrl + "/browse/PROJ-PLAN-BR1", widget.table().rows().get(1).url());

        // pas encore de build sur cette branche : repli propre sur "?"
        assertEquals(List.of("feature-y", "?", "?", "?", "?"), widget.table().rows().get(2).cells());

        assertEquals("Bearer fake-token", capturedAuth.get());
    }

    @Test
    void limitsBranchesQueriedToMaxBranches() throws Exception {
        responses.put("/rest/api/latest/result/PROJ-PLAN.json", "{\"results\":{\"result\":[]}}");
        responses.put("/rest/api/latest/plan/PROJ-PLAN.json", branchesJson(7));
        // seules les 2 premières branches doivent être interrogées : les
        // autres ne sont pas enregistrées, une requête dessus ferait
        // échouer le test avec un 404.
        responses.put("/rest/api/latest/result/PROJ-PLAN-BR0.json", "{\"results\":{\"result\":[]}}");
        responses.put("/rest/api/latest/result/PROJ-PLAN-BR1.json", "{\"results\":{\"result\":[]}}");

        var extractor = new BuildStatusExtractor(baseUrl, "fake-token");
        List<ExtractorWidget> widgets = extractor.fetch(planResource(2));

        assertEquals(3, widgets.get(0).table().rows().size());
    }

    @Test
    void defaultsMaxBranchesToFiveWhenNotSetOnResource() throws Exception {
        responses.put("/rest/api/latest/result/PROJ-PLAN.json", "{\"results\":{\"result\":[]}}");
        responses.put("/rest/api/latest/plan/PROJ-PLAN.json", branchesJson(6));
        for (int i = 0; i < 5; i++) {
            responses.put("/rest/api/latest/result/PROJ-PLAN-BR" + i + ".json", "{\"results\":{\"result\":[]}}");
        }

        var extractor = new BuildStatusExtractor(baseUrl, "fake-token");
        List<ExtractorWidget> widgets = extractor.fetch(planResourceDefaultMaxBranches());

        assertEquals(6, widgets.get(0).table().rows().size());
    }

    @Test
    void throwsExplicitErrorOnHttpFailure() throws Exception {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        var extractor = new BuildStatusExtractor(baseUrl, "fake-token");

        Exception ex = assertThrows(RuntimeException.class, () -> extractor.fetch(planResourceDefaultMaxBranches()));
        assertTrue(ex.getMessage().contains("401"));
    }

    private static String branchesJson(int count) {
        StringBuilder branches = new StringBuilder("{\"branches\":{\"branch\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                branches.append(",");
            }
            branches.append("{\"key\":\"PROJ-PLAN-BR").append(i).append("\",\"shortName\":\"branch-").append(i).append("\"}");
        }
        return branches.append("]}}").toString();
    }
}
