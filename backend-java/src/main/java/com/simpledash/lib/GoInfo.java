package com.simpledash.lib;

import java.util.Map;

public record GoInfo(String module, String goVersion, Map<String, String> dependencies) {}
