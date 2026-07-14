package com.simpledash.domains.jira;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ResourceList;
import java.util.List;

public class JiraDomain implements Domain {

    private final List<Extractor> extractors = List.of(new IssuesExtractor());

    public String id() {
        return "jira";
    }

    public String name() {
        return "Jira";
    }

    public ResourceList listResources() {
        return new ResourceList(JiraConfig.RESOURCES, List.of());
    }

    public List<Extractor> extractors() {
        return extractors;
    }
}
