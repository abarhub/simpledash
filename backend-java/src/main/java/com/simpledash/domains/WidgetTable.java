package com.simpledash.domains;

import java.util.List;

public record WidgetTable(List<String> columns, List<WidgetRow> rows) {}
