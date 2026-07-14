package com.simpledash.domains.bitbucket;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.ResourceList;
import java.util.List;

public class BitbucketDomain implements Domain {

    private final List<Extractor> extractors = List.of(new PullRequestsExtractor());

    public String id() {
        return "bitbucket";
    }

    public String name() {
        return "Bitbucket";
    }

    public ResourceList listResources() {
        return new ResourceList(BitbucketConfig.RESOURCES, List.of());
    }

    public List<Extractor> extractors() {
        return extractors;
    }
}
