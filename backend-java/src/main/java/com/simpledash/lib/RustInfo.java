package com.simpledash.lib;

import java.util.List;
import java.util.Map;

public record RustInfo(
    String name,
    String version,
    String rustVersion,
    Map<String, String> dependencies,
    List<String> workspaceMembers
) {}
