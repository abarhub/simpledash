package com.simpledash.domains.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.simpledash.domains.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerInfoExtractorTest {

    @Test
    void includesUrlWhenPresent() throws Exception {
        var extractor = new ServerInfoExtractor();
        var resource = new Resource("srv-1", "Serveur Web", List.of("http"), Map.of("url", "https://example.com"));

        var widgets = extractor.fetch(resource);
        assertEquals(1, widgets.size());
        assertEquals(Map.of("Type", "http", "URL", "https://example.com"), widgets.get(0).data());
    }

    @Test
    void includesHostWhenPresent() throws Exception {
        var extractor = new ServerInfoExtractor();
        var resource = new Resource("srv-2", "Serveur DB", List.of("ssh"), Map.of("host", "db.internal.local"));

        var widgets = extractor.fetch(resource);
        assertEquals(1, widgets.size());
        assertEquals(Map.of("Type", "ssh", "Host", "db.internal.local"), widgets.get(0).data());
    }

    @Test
    void joinsMultipleTypesWithComma() throws Exception {
        var extractor = new ServerInfoExtractor();
        var resource = new Resource("srv-3", "Serveur mixte", List.of("http", "ssh"), Map.of());

        var widgets = extractor.fetch(resource);
        assertEquals("http, ssh", widgets.get(0).data().get("Type"));
    }
}
