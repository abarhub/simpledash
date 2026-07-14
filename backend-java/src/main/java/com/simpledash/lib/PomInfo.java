package com.simpledash.lib;

import java.util.List;
import java.util.Map;

public record PomInfo(
    ParentInfo parent,
    String groupId,
    String artifactId,
    String version,
    Map<String, String> properties,
    List<Dependency> dependencies,
    List<String> modules
) {}
