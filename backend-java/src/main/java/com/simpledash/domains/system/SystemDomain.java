package com.simpledash.domains.system;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.Resource;
import com.simpledash.domains.ResourceList;
import java.util.List;

public class SystemDomain implements Domain {

    private static final List<Resource> RESOURCES = List.of(
        new Resource("local", "Cette machine", List.of("local"))
    );

    private final List<Extractor> extractors = List.of(
        new DatetimeExtractor(),
        new SystemInfoExtractor(),
        new JokeExtractor()
    );

    public String id() {
        return "system";
    }

    public String name() {
        return "Système";
    }

    public ResourceList listResources() {
        return new ResourceList(RESOURCES, List.of());
    }

    public List<Extractor> extractors() {
        return extractors;
    }
}
