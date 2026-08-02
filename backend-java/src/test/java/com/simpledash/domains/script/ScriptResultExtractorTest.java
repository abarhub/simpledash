package com.simpledash.domains.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptResultExtractorTest {

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
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static Resource scriptResource(Map<String, Object> extra) {
        return new Resource("mon-script", "Mon script", List.of("script"), extra);
    }

    private static Map<String, Object> inlineScript(String script) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("script", script);
        extra.put("file", null);
        extra.put("params", Map.of());
        extra.put("timeoutSeconds", null);
        return extra;
    }

    @Test
    void runsInlineScriptAndReturnsDataWidget() throws Exception {
        var extractor = new ScriptResultExtractor(List.of());
        var resource = scriptResource(inlineScript("""
            function run(api) {
              return { data: { statut: "OK" } };
            }
            """));

        List<ExtractorWidget> widgets = extractor.fetch(resource);

        assertEquals(1, widgets.size());
        assertEquals("Mon script", widgets.get(0).title());
        assertEquals(Map.of("statut", "OK"), widgets.get(0).data());
        assertNull(widgets.get(0).table());
    }

    @Test
    void runsFileScriptAndReturnsTableWidget(@TempDir Path dir) throws Exception {
        Path scriptFile = dir.resolve("exemple.js");
        Files.writeString(scriptFile, """
            function run(api) {
              return {
                title: "Builds",
                table: {
                  columns: ["Nom", "Statut"],
                  rows: [
                    { cells: ["build-1", "OK"], url: "https://example.com/build-1" },
                    ["build-2", "KO"]
                  ]
                }
              };
            }
            """);
        Map<String, Object> extra = new HashMap<>();
        extra.put("script", null);
        extra.put("file", scriptFile.toString());
        extra.put("params", Map.of());
        extra.put("timeoutSeconds", null);

        var extractor = new ScriptResultExtractor(List.of());
        List<ExtractorWidget> widgets = extractor.fetch(scriptResource(extra));

        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals("Builds", widget.title());
        assertEquals(List.of("Nom", "Statut"), widget.table().columns());
        assertEquals(2, widget.table().rows().size());
        assertEquals(List.of("build-1", "OK"), widget.table().rows().get(0).cells());
        assertEquals("https://example.com/build-1", widget.table().rows().get(0).url());
        assertEquals(List.of("build-2", "KO"), widget.table().rows().get(1).cells());
        assertNull(widget.table().rows().get(1).url());
    }

    @Test
    void scriptCanCallApiFetch() throws Exception {
        var extractor = new ScriptResultExtractor(List.of());
        var resource = scriptResource(inlineScript("""
            function run(api) {
              var body = api.fetch("%s/health");
              return { data: { reponse: body } };
            }
            """.formatted(baseUrl)));

        List<ExtractorWidget> widgets = extractor.fetch(resource);

        assertEquals("OK", widgets.get(0).data().get("reponse"));
    }

    @Test
    void apiFetchResultBehavesAsNativeJsString() throws Exception {
        // Régression : Rhino wrappe par défaut les String renvoyées par un
        // appel Java comme de vrais objets Java (javaPrimitiveWrap=true),
        // exposant leurs méthodes Java (ex: .length() apparaît comme une
        // fonction) plutôt que de se comporter comme une primitive JS
        // (typeof "string", .length un nombre) — ScriptRunner désactive ce
        // réglage ; ce test verrouille ce comportement.
        var extractor = new ScriptResultExtractor(List.of());
        var resource = scriptResource(inlineScript("""
            function run(api) {
              var body = api.fetch("%s/health");
              return { data: { type: typeof body, longueur: String(body.length) } };
            }
            """.formatted(baseUrl)));

        List<ExtractorWidget> widgets = extractor.fetch(resource);

        assertEquals("string", widgets.get(0).data().get("type"));
        assertEquals("2", widgets.get(0).data().get("longueur"));
    }

    @Test
    void scriptCanCallApiReadFile(@TempDir Path dir) throws Exception {
        Path dataFile = dir.resolve("data.txt");
        Files.writeString(dataFile, "contenu du fichier");

        var extractor = new ScriptResultExtractor(List.of(dir));
        var resource = scriptResource(inlineScript("""
            function run(api) {
              var content = api.readFile("%s");
              return { data: { contenu: content } };
            }
            """.formatted(dataFile.toString().replace("\\", "\\\\"))));

        List<ExtractorWidget> widgets = extractor.fetch(resource);

        assertEquals("contenu du fichier", widgets.get(0).data().get("contenu"));
    }

    @Test
    void scriptCanAccessParams() throws Exception {
        Map<String, Object> extra = new HashMap<>();
        extra.put("script", """
            function run(api) {
              return { data: { salutation: api.params.greeting } };
            }
            """);
        extra.put("file", null);
        extra.put("params", Map.of("greeting", "bonjour"));
        extra.put("timeoutSeconds", null);

        var extractor = new ScriptResultExtractor(List.of());
        List<ExtractorWidget> widgets = extractor.fetch(scriptResource(extra));

        assertEquals("bonjour", widgets.get(0).data().get("salutation"));
    }

    @Test
    void throwsClearErrorOnSyntaxError() {
        var extractor = new ScriptResultExtractor(List.of());
        var resource = scriptResource(inlineScript("function run(api) { return"));

        assertThrows(Exception.class, () -> extractor.fetch(resource));
    }

    @Test
    void throwsClearErrorWhenRunFunctionMissing() {
        var extractor = new ScriptResultExtractor(List.of());
        var resource = scriptResource(inlineScript("var x = 1;"));

        Exception ex = assertThrows(RuntimeException.class, () -> extractor.fetch(resource));
        assertTrue(ex.getMessage().contains("run(api)"));
    }

    @Test
    void throwsClearErrorWhenBothScriptAndFileSet() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("script", "function run(api) { return {data:{}}; }");
        extra.put("file", "D:/scripts/exemple.js");
        extra.put("params", Map.of());
        extra.put("timeoutSeconds", null);

        var extractor = new ScriptResultExtractor(List.of());
        Exception ex = assertThrows(RuntimeException.class, () -> extractor.fetch(scriptResource(extra)));
        assertTrue(ex.getMessage().contains("exactement un"));
    }

    @Test
    void throwsClearErrorWhenNeitherScriptNorFileSet() {
        var extractor = new ScriptResultExtractor(List.of());
        var resource = scriptResource(inlineScript(null));

        assertThrows(RuntimeException.class, () -> extractor.fetch(resource));
    }

    @Test
    void runawayScriptIsInterruptedWithinBoundedTime() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("script", "function run(api) { while(true) {} }");
        extra.put("file", null);
        extra.put("params", Map.of());
        extra.put("timeoutSeconds", 1);

        var extractor = new ScriptResultExtractor(List.of());
        long start = System.currentTimeMillis();

        Exception ex = assertThrows(Exception.class, () -> extractor.fetch(scriptResource(extra)));

        long elapsedMs = System.currentTimeMillis() - start;
        assertTrue(elapsedMs < 5000, "le script aurait dû être interrompu bien avant 5s, a pris " + elapsedMs + "ms");
        assertTrue(ex.getMessage().contains("délai"));
    }
}
