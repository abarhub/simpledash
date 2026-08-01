package com.simpledash.domains.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simpledash.domains.projects.ProjectsConfig.RawConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectsConfigTest {

    @Test
    void loadFileReturnsEmptyConfigWhenFileMissing(@TempDir Path dir) {
        RawConfig config = ProjectsConfig.loadFile(dir.resolve("absent.yml"));

        assertTrue(config.scanRoots().isEmpty());
        assertTrue(config.ignoreDirs().isEmpty());
        assertTrue(config.groups().isEmpty());
    }

    @Test
    void loadFileParsesScanRootsIgnoreDirsAndGroups(@TempDir Path dir) throws IOException {
        Path configFile = dir.resolve("projects.yml");
        Files.writeString(configFile, """
            scanRoots:
              - D:/projet/mon-appli-java
              - D:/projet/dossier-avec-plusieurs-projets
            ignoreDirs:
              - dossier-a-ignorer
            groups:
              - id: mes-projets-java
                name: Mes projets Java
                paths:
                  - D:/projet/mon-appli-java/module-a
                  - D:/projet/mon-appli-java/module-b
            """);

        RawConfig config = ProjectsConfig.loadFile(configFile);

        assertEquals(List.of("D:/projet/mon-appli-java", "D:/projet/dossier-avec-plusieurs-projets"), config.scanRoots());
        assertEquals(List.of("dossier-a-ignorer"), config.ignoreDirs());
        assertEquals(1, config.groups().size());
        assertEquals("mes-projets-java", config.groups().get(0).id());
        assertEquals("Mes projets Java", config.groups().get(0).name());
        assertEquals(
            List.of("D:/projet/mon-appli-java/module-a", "D:/projet/mon-appli-java/module-b"),
            config.groups().get(0).paths()
        );
    }

    @Test
    void loadFileHandlesMissingOptionalSections(@TempDir Path dir) throws IOException {
        Path configFile = dir.resolve("projects.yml");
        Files.writeString(configFile, "scanRoots:\n  - D:/projet/mon-appli-java\n");

        RawConfig config = ProjectsConfig.loadFile(configFile);

        assertEquals(List.of("D:/projet/mon-appli-java"), config.scanRoots());
        assertTrue(config.ignoreDirs().isEmpty());
        assertTrue(config.groups().isEmpty());
    }
}
