package com.simpledash.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvTest {

    @Test
    void parseIgnoresBlankLinesAndComments() {
        Map<String, String> result = Env.parse(List.of(
            "# commentaire",
            "",
            "  ",
            "JIRA_BASE_URL=https://jira.exemple.local",
            "JIRA_TOKEN="
        ));

        assertEquals("https://jira.exemple.local", result.get("JIRA_BASE_URL"));
        assertEquals("", result.get("JIRA_TOKEN"));
        assertEquals(2, result.size());
    }

    @Test
    void parseTrimsKeysAndValues() {
        Map<String, String> result = Env.parse(List.of("  SONAR_TOKEN  =  abc123  "));

        assertEquals("abc123", result.get("SONAR_TOKEN"));
    }

    @Test
    void parseStripsMatchingQuotes() {
        Map<String, String> result = Env.parse(List.of(
            "BAMBOO_BASE_URL=\"https://bamboo.exemple.local\"",
            "BAMBOO_TOKEN='secret'"
        ));

        assertEquals("https://bamboo.exemple.local", result.get("BAMBOO_BASE_URL"));
        assertEquals("secret", result.get("BAMBOO_TOKEN"));
    }

    @Test
    void parseIgnoresLinesWithoutEquals() {
        Map<String, String> result = Env.parse(List.of("PAS_UNE_VARIABLE"));

        assertTrue(result.isEmpty());
    }

    @Test
    void loadFileReturnsEmptyMapWhenFileMissing(@TempDir Path dir) {
        Map<String, String> result = Env.loadFile(dir.resolve("absent.env"));

        assertTrue(result.isEmpty());
    }

    @Test
    void loadFileReadsRealFile(@TempDir Path dir) throws IOException {
        Path envFile = dir.resolve(".env");
        Files.writeString(envFile, "BITBUCKET_USERNAME=jdupont\n");

        Map<String, String> result = Env.loadFile(envFile);

        assertEquals("jdupont", result.get("BITBUCKET_USERNAME"));
    }

    @Test
    void resolveValuePrefersRealEnvironmentVariable() {
        String realPath = System.getenv("PATH");
        assertTrue(realPath != null && !realPath.isBlank(), "PATH doit exister pour ce test");

        String resolved = Env.resolveValue("PATH", Map.of("PATH", "valeur-du-fichier-ignoree"));

        assertEquals(realPath, resolved);
    }

    @Test
    void resolveValueFallsBackToFileWhenNoRealEnvironmentVariable() {
        String key = "SIMPLEDASH_TEST_ONLY_VAR_NOT_REAL";
        assertNull(System.getenv(key), "cette variable ne doit pas exister dans l'environnement réel");

        String resolved = Env.resolveValue(key, Map.of(key, "valeur-du-fichier"));

        assertEquals("valeur-du-fichier", resolved);
    }
}
