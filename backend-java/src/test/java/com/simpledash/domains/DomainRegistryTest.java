package com.simpledash.domains;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainRegistryTest {

    static class FakeExtractor implements Extractor {
        private final String id;
        private final List<String> compatibleTypes;
        private final boolean fail;

        FakeExtractor(String id, List<String> compatibleTypes, boolean fail) {
            this.id = id;
            this.compatibleTypes = compatibleTypes;
            this.fail = fail;
        }

        public String id() {
            return id;
        }

        public String name() {
            return "Fake " + id;
        }

        public String description() {
            return "desc";
        }

        public List<String> compatibleTypes() {
            return compatibleTypes;
        }

        public List<ExtractorWidget> fetch(Resource resource) {
            if (fail) throw new RuntimeException("boom");
            return List.of(ExtractorWidget.data("w1", "Widget " + id, Map.of("Clé", "Valeur")));
        }
    }

    static class FakeDomain implements Domain {
        private final List<Resource> resources;
        private final List<Extractor> extractors;

        FakeDomain(List<Resource> resources, List<Extractor> extractors) {
            this.resources = resources;
            this.extractors = extractors;
        }

        public String id() {
            return "fake";
        }

        public String name() {
            return "Fake";
        }

        public ResourceList listResources() {
            return new ResourceList(resources, List.of());
        }

        public List<Extractor> extractors() {
            return extractors;
        }
    }

    @Test
    void listDomainsReturnsIdAndName() {
        var domain = new FakeDomain(List.of(), List.of());
        var registry = new DomainRegistry(List.of(domain));

        var domains = registry.listDomains();
        assertEquals(1, domains.size());
        assertEquals("fake", domains.get(0).get("id"));
        assertEquals("Fake", domains.get(0).get("name"));
    }

    @Test
    void listExtractorsFiltersByResourceType() {
        var resource = new Resource("r1", "Ressource 1", List.of("typeA"));
        var extractorA = new FakeExtractor("ext-a", List.of("typeA"), false);
        var extractorB = new FakeExtractor("ext-b", List.of("typeB"), false);
        var domain = new FakeDomain(List.of(resource), List.of(extractorA, extractorB));
        var registry = new DomainRegistry(List.of(domain));

        var extractors = registry.listExtractors("fake", List.of("r1"));
        assertEquals(1, extractors.size());
        assertEquals("ext-a", extractors.get(0).id());
    }

    @Test
    void getWidgetsComposesIdAndMetadata() {
        var resource = new Resource("r1", "Ressource 1", List.of("typeA"));
        var extractor = new FakeExtractor("ext-a", List.of("typeA"), false);
        var domain = new FakeDomain(List.of(resource), List.of(extractor));
        var registry = new DomainRegistry(List.of(domain));

        var widgets = registry.getWidgets("fake", List.of("r1"), List.of("ext-a"));
        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals("r1-ext-a-w1", widget.id());
        assertEquals("r1", widget.resourceId());
        assertEquals("Ressource 1", widget.resourceName());
        assertEquals("ext-a", widget.extractorId());
        assertNull(widget.error());
    }

    @Test
    void getWidgetsCatchesExtractorErrorsAsErrorWidgets() {
        var resource = new Resource("r1", "Ressource 1", List.of("typeA"));
        var extractor = new FakeExtractor("ext-a", List.of("typeA"), true);
        var domain = new FakeDomain(List.of(resource), List.of(extractor));
        var registry = new DomainRegistry(List.of(domain));

        var widgets = registry.getWidgets("fake", List.of("r1"), List.of("ext-a"));
        assertEquals(1, widgets.size());
        assertEquals("boom", widgets.get(0).error());
    }

    @Test
    void getWidgetsSkipsIncompatibleResourceExtractorPairs() {
        var resource = new Resource("r1", "Ressource 1", List.of("typeA"));
        var extractor = new FakeExtractor("ext-b", List.of("typeB"), false);
        var domain = new FakeDomain(List.of(resource), List.of(extractor));
        var registry = new DomainRegistry(List.of(domain));

        var widgets = registry.getWidgets("fake", List.of("r1"), List.of("ext-b"));
        assertEquals(0, widgets.size());
    }
}
