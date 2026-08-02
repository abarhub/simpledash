package com.simpledash.domains.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.script.ScriptConfig.RawConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptConfigTest {

    @Test
    void loadFileReturnsEmptyConfigWhenFileMissing(@TempDir Path dir) {
        RawConfig config = ScriptConfig.loadFile(dir.resolve("absent.yml"));

        assertTrue(config.readFileRoots().isEmpty());
        assertTrue(config.scripts().isEmpty());
    }

    @Test
    void loadFileParsesInlineScriptResource(@TempDir Path dir) throws IOException {
        Path configFile = dir.resolve("scripts.yml");
        Files.writeString(configFile, """
            scripts:
              - id: exemple-inline
                name: Exemple inline
                script: |
                  function run(api) {
                    return { data: { statut: "OK" } };
                  }
            """);

        RawConfig config = ScriptConfig.loadFile(configFile);

        assertEquals(1, config.scripts().size());
        var script = config.scripts().get(0);
        assertEquals("exemple-inline", script.id());
        assertEquals("Exemple inline", script.name());
        assertEquals("function run(api) {\n  return { data: { statut: \"OK\" } };\n}\n", script.script());
        assertNull(script.file());
    }

    @Test
    void loadFileParsesFileReferenceResource(@TempDir Path dir) throws IOException {
        Path configFile = dir.resolve("scripts.yml");
        Files.writeString(configFile, """
            scripts:
              - id: exemple-fichier
                name: Exemple fichier
                file: D:/scripts/exemple.js
            """);

        RawConfig config = ScriptConfig.loadFile(configFile);

        var script = config.scripts().get(0);
        assertEquals("D:/scripts/exemple.js", script.file());
        assertNull(script.script());
    }

    @Test
    void loadFileParsesParamsMap(@TempDir Path dir) throws IOException {
        Path configFile = dir.resolve("scripts.yml");
        Files.writeString(configFile, """
            scripts:
              - id: exemple
                name: Exemple
                file: D:/scripts/exemple.js
                params:
                  url: https://example.com/health
                  seuil: 200
            """);

        RawConfig config = ScriptConfig.loadFile(configFile);

        Map<String, Object> params = config.scripts().get(0).params();
        assertEquals("https://example.com/health", params.get("url"));
        assertEquals(200, params.get("seuil"));
    }

    @Test
    void loadFileParsesTimeoutSecondsAndReadFileRoots(@TempDir Path dir) throws IOException {
        Path configFile = dir.resolve("scripts.yml");
        Files.writeString(configFile, """
            readFileRoots:
              - D:/scripts
            scripts:
              - id: exemple
                name: Exemple
                file: D:/scripts/exemple.js
                timeoutSeconds: 30
            """);

        RawConfig config = ScriptConfig.loadFile(configFile);

        assertEquals(List.of("D:/scripts"), config.readFileRoots());
        assertEquals(30, config.scripts().get(0).timeoutSeconds());
    }

    @Test
    void loadFileHandlesMissingOptionalSections(@TempDir Path dir) throws IOException {
        Path configFile = dir.resolve("scripts.yml");
        Files.writeString(configFile, """
            scripts:
              - id: exemple
                name: Exemple
                script: |
                  function run(api) { return { data: {} }; }
            """);

        RawConfig config = ScriptConfig.loadFile(configFile);

        assertTrue(config.readFileRoots().isEmpty());
        var script = config.scripts().get(0);
        assertNull(script.params());
        assertNull(script.timeoutSeconds());
    }
}
