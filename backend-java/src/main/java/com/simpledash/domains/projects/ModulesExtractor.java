package com.simpledash.domains.projects;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import com.simpledash.lib.AnalyzeProject;
import com.simpledash.lib.AnalyzedProject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModulesExtractor implements Extractor {

    public String id() {
        return "modules";
    }

    public String name() {
        return "Modules";
    }

    public String description() {
        return "Un widget par sous-module détecté (Maven, npm workspaces, Cargo, Go)";
    }

    public List<String> compatibleTypes() {
        return List.of("npm", "maven", "rust", "go");
    }

    public List<ExtractorWidget> fetch(Resource resource) throws Exception {
        var project = AnalyzeProject.analyzeProject((Path) resource.get("path"));
        List<AnalyzedProject> modules = flatten(project);

        if (modules.isEmpty()) {
            return List.of(ExtractorWidget.data("none", "Modules", Map.of("Modules", "aucun sous-module détecté")));
        }

        List<ExtractorWidget> widgets = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            var module = modules.get(i);
            widgets.add(ExtractorWidget.data("module-" + i, titleFor(module), Map.of(
                "Version", versionFor(module),
                "Chemin", module.dir().toString()
            )));
        }
        return widgets;
    }

    private static List<AnalyzedProject> flatten(AnalyzedProject project) {
        List<AnalyzedProject> result = new ArrayList<>();
        for (var module : project.modules()) {
            result.add(module);
            result.addAll(flatten(module));
        }
        return result;
    }

    private static String titleFor(AnalyzedProject module) {
        if (module.pom() != null && module.pom().artifactId() != null) return module.pom().artifactId();
        if (module.npm() != null && module.npm().name() != null) return module.npm().name();
        if (module.rust() != null && module.rust().name() != null) return module.rust().name();
        if (module.go() != null && module.go().module() != null) return module.go().module();
        return module.dir().getFileName().toString();
    }

    private static String versionFor(AnalyzedProject module) {
        if (module.pom() != null && module.pom().version() != null) return module.pom().version();
        if (module.npm() != null && module.npm().version() != null) return module.npm().version();
        if (module.rust() != null && module.rust().version() != null) return module.rust().version();
        if (module.go() != null && module.go().goVersion() != null) return module.go().goVersion();
        return "?";
    }
}
