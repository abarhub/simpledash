package com.simpledash.domains;

import java.util.List;
import java.util.Map;

public record Resource(String id, String name, List<String> types, Map<String, Object> extra) {

    public Resource(String id, String name, List<String> types) {
        this(id, name, types, Map.of());
    }

    public Object get(String key) {
        return extra.get(key);
    }
}
