package com.simpledash.domains.bamboo;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ResourceList;
import java.util.List;

public class BambooDomain implements Domain {

    private final List<Extractor> extractors = List.of(new BuildStatusExtractor());

    public String id() {
        return "bamboo";
    }

    public String name() {
        return "Bamboo";
    }

    public ResourceList listResources() {
        return new ResourceList(BambooConfig.RESOURCES, List.of());
    }

    public List<Extractor> extractors() {
        return extractors;
    }
}
