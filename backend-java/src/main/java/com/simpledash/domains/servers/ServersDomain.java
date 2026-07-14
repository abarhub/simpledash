package com.simpledash.domains.servers;

import com.simpledash.domains.Domain;
import com.simpledash.domains.Extractor;
import com.simpledash.domains.Group;
import com.simpledash.domains.Resource;
import com.simpledash.domains.ResourceList;
import java.util.List;
import java.util.Map;

public class ServersDomain implements Domain {

    private static final List<Resource> RESOURCES = List.of(
        new Resource("srv-web-1", "Serveur Web 1", List.of("http"), Map.of("url", "https://example.com")),
        new Resource("srv-web-2", "Serveur Web 2", List.of("http"), Map.of("url", "https://example.org")),
        new Resource("srv-db-1", "Serveur DB", List.of("ssh"), Map.of("host", "db.internal.local"))
    );

    private static final List<Group> GROUPS = List.of(
        new Group("all-servers", "Tous les serveurs", List.of("srv-web-1", "srv-web-2", "srv-db-1")),
        new Group("windows-servers", "Serveurs Windows", List.of("srv-web-1")),
        new Group("linux-servers", "Serveurs Linux", List.of("srv-web-2", "srv-db-1"))
    );

    private final List<Extractor> extractors = List.of(
        new HttpStatusExtractor(),
        new ServerInfoExtractor()
    );

    public String id() {
        return "servers";
    }

    public String name() {
        return "Serveurs";
    }

    public ResourceList listResources() {
        return new ResourceList(RESOURCES, GROUPS);
    }

    public List<Extractor> extractors() {
        return extractors;
    }
}
