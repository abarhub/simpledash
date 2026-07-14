package com.simpledash.lib;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Portage de backend/src/lib/findProjects.js : cherche récursivement, à
// partir de rootDir, les répertoires contenant un fichier marqueur de
// projet. Une fois un marqueur trouvé dans un répertoire, ses
// sous-répertoires ne sont pas explorés.
public class FindProjects {

    public static final Map<String, String> PROJECT_MARKERS = Map.of(
        "pom.xml", "maven",
        "package.json", "npm",
        "Cargo.toml", "rust",
        "go.mod", "go"
    );

    private static final Set<String> DEFAULT_IGNORE_DIRS = Set.of(
        "node_modules", "target", "dist", "build", "out", ".git",
        "venv", ".venv", "env", "__pycache__"
    );

    public record FoundProject(Path dir, List<String> files) {}

    public static List<FoundProject> findProjects(Path rootDir) {
        return findProjects(rootDir, Set.of());
    }

    public static List<FoundProject> findProjects(Path rootDir, Set<String> extraIgnoreDirs) {
        Set<String> ignoreDirs = new LinkedHashSet<>(DEFAULT_IGNORE_DIRS);
        ignoreDirs.addAll(extraIgnoreDirs);

        List<FoundProject> results = new ArrayList<>();
        walk(rootDir, ignoreDirs, results);
        return results;
    }

    private static void walk(Path dir, Set<String> ignoreDirs, List<FoundProject> results) {
        List<String> foundMarkers = new ArrayList<>();
        List<Path> subDirs = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (Files.isRegularFile(entry) && PROJECT_MARKERS.containsKey(name)) {
                    foundMarkers.add(name);
                } else if (Files.isDirectory(entry) && !ignoreDirs.contains(name)) {
                    subDirs.add(entry);
                }
            }
        } catch (IOException e) {
            return;
        }

        if (!foundMarkers.isEmpty()) {
            results.add(new FoundProject(dir, foundMarkers));
            return;
        }

        for (Path subDir : subDirs) {
            walk(subDir, ignoreDirs, results);
        }
    }
}
