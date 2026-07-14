package com.simpledash.domains.projects;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.lib.AnalyzeProject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class NpmVersionExtractor implements Extractor {

    public String id() {
        return "npm-version";
    }

    public String name() {
        return "Version (package.json)";
    }

    public String description() {
        return "Champ \"version\" du package.json";
    }

    public List<String> compatibleTypes() {
        return List.of("npm");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        var project = AnalyzeProject.analyzeProject((Path) resource.get("path"));
        String version = project.npm() != null ? project.npm().version() : null;
        return List.of(ExtractorWidget.data("version", "Version", Map.of(
            "Version", version != null ? version : "introuvable"
        )));
    }
}
