package com.simpledash.domains.projects;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.Group;
import com.simpledash.domains.Resource;
import com.simpledash.domains.ResourceList;
import com.simpledash.lib.FindProjects;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProjectsDomain implements Domain {

    private final List<Extractor> extractors = List.of(
        new NpmVersionExtractor(),
        new NpmDependenciesExtractor(),
        new PomVersionExtractor(),
        new SummaryExtractor(),
        new ModulesExtractor(),
        new GitInfoExtractor()
    );

    public String id() {
        return "projects";
    }

    public String name() {
        return "Projets";
    }

    // Scanne les racines déclarées en config à chaque appel (pas de cache) :
    // simple, et cohérent avec le reste de l'appli qui se rafraîchit à la
    // demande plutôt qu'en arrière-plan.
    public ResourceList listResources() {
        List<FindProjects.FoundProject> found = new ArrayList<>();
        for (Path root : ProjectsConfig.SCAN_ROOTS) {
            found.addAll(FindProjects.findProjects(root, ProjectsConfig.IGNORE_DIRS));
        }

        List<Resource> resources = found.stream()
            .map(fp -> {
                List<String> types = new ArrayList<>(
                    fp.files().stream().map(FindProjects.PROJECT_MARKERS::get).distinct().toList()
                );
                if (Files.isDirectory(fp.dir().resolve(".git"))) {
                    types.add("git");
                }
                return new Resource(
                    resourceId(fp.dir()),
                    fp.dir().getFileName().toString(),
                    types,
                    Map.of("path", fp.dir())
                );
            })
            .toList();

        Map<Path, String> idByResolvedPath = new HashMap<>();
        for (Resource r : resources) {
            idByResolvedPath.put(((Path) r.get("path")).toAbsolutePath().normalize(), r.id());
        }

        List<Group> groups = new ArrayList<>();
        for (var configured : ProjectsConfig.GROUPS) {
            List<String> resourceIds = configured.paths().stream()
                .map(p -> idByResolvedPath.get(p.toAbsolutePath().normalize()))
                .filter(Objects::nonNull)
                .toList();
            groups.add(new Group(configured.id(), configured.name(), resourceIds));
        }
        if (!resources.isEmpty()) {
            groups.add(new Group("all-projects", "Tous les projets", resources.stream().map(Resource::id).toList()));
        }

        return new ResourceList(resources, groups);
    }

    public List<Extractor> extractors() {
        return extractors;
    }

    private static String resourceId(Path dir) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(dir.toString().getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash).substring(0, 8);
            return dir.getFileName().toString() + "-" + hex;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
