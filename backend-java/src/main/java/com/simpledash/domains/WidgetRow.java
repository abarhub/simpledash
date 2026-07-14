package com.simpledash.domains;

import java.util.List;

public record WidgetRow(String url, List<String> cells) {

    public WidgetRow(List<String> cells) {
        this(null, cells);
    }
}
