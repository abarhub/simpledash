package com.simpledash.domains.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Config du domaine projects, chargée depuis projects.yml (voir
// projects.yml.example) au premier accès — suppose un lancement depuis
// backend-java/, comme Env. Absent, le domaine reste vide (pas d'erreur),
// même comportement que les domaines authentifiés sans .env.
public final class ProjectsConfig {

    public record ConfiguredGroup(String id, String name, List<Path> paths) {}

    record RawGroup(String id, String name, List<String> paths) {}

    record RawConfig(List<String> scanRoots, List<String> ignoreDirs, List<RawGroup> groups) {}

    private static final Path CONFIG_FILE = Path.of("projects.yml");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final RawConfig EMPTY = new RawConfig(List.of(), List.of(), List.of());

    // Racines scannées par FindProjects (un ou plusieurs répertoires).
    public static final List<Path> SCAN_ROOTS;

    // Noms de répertoires ignorés pendant le scan, en plus des ignorés par
    // défaut de FindProjects (node_modules, target, dist, .git, etc.).
    public static final Set<String> IGNORE_DIRS;

    // Groupes déclarés par chemin exact (pas par id, généré dynamiquement
    // au scan). Un groupe "Tous les projets" est toujours ajouté
    // automatiquement en plus, par ProjectsDomain.
    public static final List<ConfiguredGroup> GROUPS;

    static {
        RawConfig raw = loadFile(CONFIG_FILE);
        SCAN_ROOTS = toPaths(raw.scanRoots());
        IGNORE_DIRS = new LinkedHashSet<>(raw.ignoreDirs());
        GROUPS = raw.groups().stream()
            .map(g -> new ConfiguredGroup(g.id(), g.name(), toPaths(g.paths())))
            .toList();
    }

    private ProjectsConfig() {}

    static RawConfig loadFile(Path path) {
        var path2=path;
        if(Files.notExists(path2)) {
            var s=System.getProperty("fichierConfig");
            if(s!=null&&!s.isBlank()) {
                path2= Paths.get(s);
            }
        }
        if (!Files.isRegularFile(path2)) {
            return EMPTY;
        }
        RawConfig parsed;
        try {
            parsed = YAML_MAPPER.readValue(path2.toFile(), RawConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de lire " + path + ": " + e.getMessage(), e);
        }
        return new RawConfig(
            parsed.scanRoots() == null ? List.of() : parsed.scanRoots(),
            parsed.ignoreDirs() == null ? List.of() : parsed.ignoreDirs(),
            parsed.groups() == null ? List.of() : parsed.groups()
        );
    }

    private static List<Path> toPaths(List<String> raw) {
        return raw == null ? List.of() : raw.stream().map(Path::of).toList();
    }
}
