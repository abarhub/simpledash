package com.simpledash.domains;

import java.util.List;

// Ce que /api/domains/{id}/extractors renvoie : pas le fetch() lui-même.
public record ExtractorSummary(String id, String name, String description, List<String> compatibleTypes) {}
