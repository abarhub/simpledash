package com.simpledash.domains.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.Resource;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpStatusExtractorTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void reportsStatusCodeAndResponseTime() throws Exception {
        var extractor = new HttpStatusExtractor();
        var resource = new Resource("srv-1", "Serveur", List.of("http"), Map.of("url", baseUrl));

        var widgets = extractor.fetch(resource);

        assertEquals(1, widgets.size());
        var data = widgets.get(0).data();
        assertEquals(baseUrl, data.get("URL"));
        assertEquals("204", data.get("Statut"));
        assertTrue(data.get("Temps de réponse").endsWith(" ms"));
    }
}
