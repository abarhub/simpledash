package com.simpledash.domains.projects;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.lib.AnalyzeProject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SummaryExtractor implements Extractor {

    public String id() {
        return "summary";
    }

    public String name() {
        return "Résumé";
    }

    public String description() {
        return "Versions clés détectées (Java, Spring Boot, Angular, Rust, Go), y compris dans les sous-modules";
    }

    public List<String> compatibleTypes() {
        return List.of("npm", "maven", "rust", "go");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        var project = AnalyzeProject.analyzeProject((Path) resource.get("path"));
        var summary = project.summary();

        Map<String, String> data = new LinkedHashMap<>();
        putIfNotEmpty(data, "Java", summary.javaVersion());
        putIfNotEmpty(data, "Spring Boot", summary.springBootVersion());
        putIfNotEmpty(data, "Angular", summary.angularVersion());
        putIfNotEmpty(data, "Rust", summary.rustVersion());
        putIfNotEmpty(data, "Go", summary.goVersion());

        if (data.isEmpty()) {
            data.put("Résumé", "aucune info détectée");
        }

        return List.of(ExtractorWidget.data("summary", "Résumé", data));
    }

    private static void putIfNotEmpty(Map<String, String> data, String label, List<String> values) {
        if (!values.isEmpty()) {
            data.put(label, String.join(", ", values));
        }
    }
}
