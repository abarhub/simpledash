package com.simpledash.lib;

import java.nio.file.Path;
import java.util.List;

public record AnalyzedProject(
    Path dir,
    PomInfo pom,
    NpmInfo npm,
    ProjectSummary summary,
    List<AnalyzedProject> modules
) {}
