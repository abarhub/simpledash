package com.simpledash.domains.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// L'exposition de "params" en objet JS natif (api.params.xxx) dépend de la
// mécanique Rhino (voir ScriptRunner) et n'est pas testable au niveau
// purement Java — couverte par ScriptResultExtractorTest.scriptCanAccessParams.
class ScriptApiTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            exchange.sendResponseHeaders(500, -1);
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
    void fetchReturnsResponseBodyOnSuccess() {
        var api = new ScriptApi(List.of());

        String body = api.fetch(baseUrl + "/health");

        assertEquals("OK", body);
    }

    @Test
    void fetchThrowsOnHttpFailure() {
        var api = new ScriptApi(List.of());

        Exception ex = assertThrows(RuntimeException.class, () -> api.fetch(baseUrl + "/fail"));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void readFileReturnsContentWhenPathIsAllowed(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.txt");
        Files.writeString(file, "contenu du fichier");
        var api = new ScriptApi(List.of(dir));

        String content = api.readFile(file.toString());

        assertEquals("contenu du fichier", content);
    }

    @Test
    void readFileRejectsPathOutsideAllowedRoots(@TempDir Path dir) throws IOException {
        Path allowedDir = dir.resolve("allowed");
        Files.createDirectory(allowedDir);
        Path outsideFile = dir.resolve("outside.txt");
        Files.writeString(outsideFile, "secret");
        var api = new ScriptApi(List.of(allowedDir));

        Exception ex = assertThrows(RuntimeException.class, () -> api.readFile(outsideFile.toString()));
        assertTrue(ex.getMessage().contains("hors des répertoires autorisés"));
    }

    @Test
    void readFileRejectsAnyPathWhenNoRootsConfigured(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.txt");
        Files.writeString(file, "contenu");
        var api = new ScriptApi(List.of());

        assertThrows(RuntimeException.class, () -> api.readFile(file.toString()));
    }
}
