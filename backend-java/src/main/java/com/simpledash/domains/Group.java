package com.simpledash.domains;

import java.util.List;

public record Group(String id, String name, List<String> resourceIds) {}
