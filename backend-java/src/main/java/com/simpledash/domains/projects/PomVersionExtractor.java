package com.simpledash.domains.projects;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.lib.AnalyzeProject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PomVersionExtractor implements Extractor {

    public String id() {
        return "pom-version";
    }

    public String name() {
        return "Version (pom.xml)";
    }

    public String description() {
        return "Balise <version> du pom.xml (projet Maven)";
    }

    public List<String> compatibleTypes() {
        return List.of("maven");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        var project = AnalyzeProject.analyzeProject((Path) resource.get("path"));
        String version = project.pom() != null ? project.pom().version() : null;
        return List.of(ExtractorWidget.data("version", "Version", Map.of(
            "Version", version != null ? version : "introuvable"
        )));
    }
}
