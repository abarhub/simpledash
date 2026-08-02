package com.simpledash.domains.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.simpledash.domains.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Config du domaine script, chargée depuis scripts.yml (voir
// scripts.yml.example) au premier accès — suppose un lancement depuis
// backend-java/, comme ProjectsConfig/Env. Absent, le domaine reste vide
// (pas d'erreur), même comportement que les autres domaines sans config.
public final class ScriptConfig {

    record RawScript(
        String id,
        String name,
        String script,
        String file,
        Map<String, Object> params,
        Integer timeoutSeconds
    ) {}

    record RawConfig(List<String> readFileRoots, List<RawScript> scripts) {}

    private static final Path CONFIG_FILE = Path.of("scripts.yml");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final RawConfig EMPTY = new RawConfig(List.of(), List.of());

    // Répertoires sous lesquels ScriptApi.readFile() est autorisé à lire.
    // Vide par défaut : aucun accès fichier tant qu'aucune racine n'est
    // déclarée (pas de repli permissif).
    public static final List<Path> READ_FILE_ROOTS;

    // Une ressource = un script configuré (inline ou référence de fichier).
    public static final List<Resource> RESOURCES;

    static {
        RawConfig raw = loadFile(CONFIG_FILE);
        READ_FILE_ROOTS = toPaths(raw.readFileRoots());
        RESOURCES = raw.scripts().stream().map(ScriptConfig::toResource).toList();
    }

    private ScriptConfig() {}

    static RawConfig loadFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return EMPTY;
        }
        RawConfig parsed;
        try {
            parsed = YAML_MAPPER.readValue(path.toFile(), RawConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de lire " + path + ": " + e.getMessage(), e);
        }
        return new RawConfig(
            parsed.readFileRoots() == null ? List.of() : parsed.readFileRoots(),
            parsed.scripts() == null ? List.of() : parsed.scripts()
        );
    }

    private static Resource toResource(RawScript s) {
        // HashMap plutôt que Map.of(...) : script/file/timeoutSeconds sont
        // nullables (Map.of rejette les valeurs null), la validation de
        // "script xor file" se fait dans ScriptResultExtractor, pas ici —
        // une ressource mal configurée ne doit jamais faire planter le
        // chargement de tout le domaine.
        Map<String, Object> extra = new HashMap<>();
        extra.put("script", s.script());
        extra.put("file", s.file());
        extra.put("params", s.params() == null ? Map.of() : s.params());
        extra.put("timeoutSeconds", s.timeoutSeconds());
        return new Resource(s.id(), s.name(), List.of("script"), extra);
    }

    private static List<Path> toPaths(List<String> raw) {
        return raw == null ? List.of() : raw.stream().map(Path::of).toList();
    }
}
