package com.simpledash.domains;

import java.util.List;

public interface Extractor {
    String id();

    String name();

    String description();

    List<String> compatibleTypes();

    List<ExtractorWidget> fetch(Resource resource) throws Exception;
}
