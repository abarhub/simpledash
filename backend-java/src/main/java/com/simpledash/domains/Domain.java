package com.simpledash.domains;

import java.util.List;

public interface Domain {
    String id();

    String name();

    ResourceList listResources();

    List<Extractor> extractors();
}
