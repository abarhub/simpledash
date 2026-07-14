package com.simpledash.domains.projects;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.lib.AnalyzeProject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NpmDependenciesExtractor implements Extractor {

    public String id() {
        return "npm-dependencies";
    }

    public String name() {
        return "Dépendances (package.json)";
    }

    public String description() {
        return "Liste des dépendances et leur version déclarée";
    }

    public List<String> compatibleTypes() {
        return List.of("npm");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        var project = AnalyzeProject.analyzeProject((Path) resource.get("path"));

        Map<String, String> deps = new LinkedHashMap<>();
        if (project.npm() != null) {
            deps.putAll(project.npm().dependencies());
            deps.putAll(project.npm().devDependencies());
        }

        return List.of(ExtractorWidget.data("dependencies", "Dépendances", deps));
    }
}
