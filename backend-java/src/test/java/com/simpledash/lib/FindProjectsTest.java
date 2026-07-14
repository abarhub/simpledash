package com.simpledash.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FindProjectsTest {

    @Test
    void findsMultipleMarkersInSameDirectoryAndStopsDescending(@TempDir Path tempDir) throws Exception {
        Path projA = tempDir.resolve("projA");
        Files.createDirectories(projA.resolve("sub"));
        Files.createFile(projA.resolve("pom.xml"));
        Files.createFile(projA.resolve("package.json"));
        // ne doit pas être remonté séparément : projA a déjà un marqueur
        Files.createFile(projA.resolve("sub").resolve("package.json"));

        var results = FindProjects.findProjects(tempDir);

        assertEquals(1, results.size());
        var found = results.get(0);
        assertEquals(projA, found.dir());
        assertEquals(Set.of("pom.xml", "package.json"), Set.copyOf(found.files()));
    }

    @Test
    void detectsRustProject(@TempDir Path tempDir) throws Exception {
        Path projB = tempDir.resolve("projB");
        Files.createDirectories(projB);
        Files.createFile(projB.resolve("Cargo.toml"));

        var results = FindProjects.findProjects(tempDir);

        assertEquals(1, results.size());
        assertEquals(List.of("Cargo.toml"), results.get(0).files());
    }

    @Test
    void ignoresNodeModulesByDefault(@TempDir Path tempDir) throws Exception {
        Path nodeModules = tempDir.resolve("node_modules").resolve("ignored-pkg");
        Files.createDirectories(nodeModules);
        Files.createFile(nodeModules.resolve("package.json"));

        var results = FindProjects.findProjects(tempDir);

        assertEquals(0, results.size());
    }

    @Test
    void extraIgnoreDirsAreExcludedOnlyWhenPassed(@TempDir Path tempDir) throws Exception {
        Path custom = tempDir.resolve("custom-ignore-me");
        Files.createDirectories(custom);
        Files.createFile(custom.resolve("package.json"));

        var withoutExtra = FindProjects.findProjects(tempDir);
        assertEquals(1, withoutExtra.size());

        var withExtra = FindProjects.findProjects(tempDir, Set.of("custom-ignore-me"));
        assertEquals(0, withExtra.size());
    }
}
