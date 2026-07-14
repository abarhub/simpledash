package com.simpledash.domains.sonar;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ResourceList;
import java.util.List;

public class SonarDomain implements Domain {

    private final List<Extractor> extractors = List.of(new QualityExtractor());

    public String id() {
        return "sonar";
    }

    public String name() {
        return "SonarQube";
    }

    public ResourceList listResources() {
        return new ResourceList(SonarConfig.RESOURCES, List.of());
    }

    public List<Extractor> extractors() {
        return extractors;
    }
}
