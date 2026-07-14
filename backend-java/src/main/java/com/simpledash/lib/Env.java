package com.simpledash.lib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Equivalent Java de {@code node --env-file-if-exists=.env} : charge un
 * fichier {@code .env} (relatif au répertoire courant) s'il existe, sans
 * erreur sinon. Une vraie variable d'environnement a toujours priorité sur
 * la valeur du fichier, comme côté Node.
 */
public final class Env {

    private static volatile Map<String, String> fileValues;

    private Env() {}

    public static String get(String key) {
        return resolveValue(key, fileValues());
    }

    private static Map<String, String> fileValues() {
        Map<String, String> values = fileValues;
        if (values == null) {
            values = loadFile(Path.of(".env"));
            fileValues = values;
        }
        return values;
    }

    static String resolveValue(String key, Map<String, String> fileValues) {
        String real = System.getenv(key);
        if (real != null && !real.isBlank()) {
            return real;
        }
        return fileValues.get(key);
    }

    static Map<String, String> loadFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            return parse(Files.readAllLines(path));
        } catch (IOException e) {
            return Map.of();
        }
    }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).strip();
            String value = unquote(trimmed.substring(eq + 1).strip());
            result.put(key, value);
        }
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
