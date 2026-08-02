package com.simpledash.domains.script;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ResourceList;
import java.util.List;

public class ScriptDomain implements Domain {

    private final List<Extractor> extractors = List.of(new ScriptResultExtractor());

    public String id() {
        return "script";
    }

    public String name() {
        return "Scripts";
    }

    public ResourceList listResources() {
        return new ResourceList(ScriptConfig.RESOURCES, List.of());
    }

    public List<Extractor> extractors() {
        return extractors;
    }
}
