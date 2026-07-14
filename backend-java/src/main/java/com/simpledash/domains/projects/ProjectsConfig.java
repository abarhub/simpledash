package com.simpledash.domains.projects;

import java.nio.file.Path;
import java.util.List;

public class ProjectsConfig {

    // Racines scannées par FindProjects. Par défaut, le repo lui-même
    // (suppose un lancement depuis backend-java/, comme documenté dans le
    // README) — adapte vers tes vrais dossiers de projets.
    public static final List<Path> SCAN_ROOTS = List.of(
        Path.of(System.getProperty("user.dir")).resolve("..").normalize()
    );

    public record ConfiguredGroup(String id, String name, List<Path> paths) {}

    // Groupes déclarés à la main, par chemin (pas par id, généré
    // dynamiquement au scan). Un groupe "Tous les projets" est toujours
    // ajouté automatiquement en plus.
    public static final List<ConfiguredGroup> GROUPS = List.of(
        new ConfiguredGroup(
            "node-projects",
            "Projets Node",
            List.of(SCAN_ROOTS.get(0).resolve("backend"), SCAN_ROOTS.get(0).resolve("frontend"))
        )
    );
}
