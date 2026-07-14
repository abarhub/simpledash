package com.simpledash.lib;

import java.util.List;
import java.util.Map;

public record NpmInfo(
    String name,
    String version,
    Map<String, String> dependencies,
    Map<String, String> devDependencies,
    List<String> workspaces
) {}
