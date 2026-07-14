package com.simpledash.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class DomainRegistry {

    private final List<Domain> domains;

    public DomainRegistry(List<Domain> domains) {
        this.domains = domains;
    }

    public List<Map<String, String>> listDomains() {
        return domains.stream().map(d -> Map.of("id", d.id(), "name", d.name())).toList();
    }

    public Domain getDomain(String id) {
        return domains.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }

    public ResourceList listResources(String domainId) {
        Domain domain = getDomain(domainId);
        return domain == null ? null : domain.listResources();
    }

    public List<ExtractorSummary> listExtractors(String domainId, List<String> resourceIds) {
        Domain domain = getDomain(domainId);
        if (domain == null) return null;

        Set<String> types = domain.listResources().resources().stream()
            .filter(r -> resourceIds.contains(r.id()))
            .flatMap(r -> r.types().stream())
            .collect(Collectors.toSet());

        return domain.extractors().stream()
            .filter(e -> e.compatibleTypes().stream().anyMatch(types::contains))
            .map(e -> new ExtractorSummary(e.id(), e.name(), e.description(), e.compatibleTypes()))
            .toList();
    }

    // Calcule le produit ressources × extracteurs compatibles, en parallèle
    // via des threads virtuels (équivalent du Promise.all côté Node), et
    // convertit toute exception d'un extracteur en widget d'erreur plutôt
    // que de faire échouer l'appel entier.
    public List<Widget> getWidgets(String domainId, List<String> resourceIds, List<String> extractorIds) {
        Domain domain = getDomain(domainId);
        if (domain == null) {
            throw new IllegalArgumentException("Domaine inconnu: " + domainId);
        }

        List<Resource> resources = domain.listResources().resources().stream()
            .filter(r -> resourceIds.contains(r.id()))
            .toList();
        List<Extractor> extractors = domain.extractors().stream()
            .filter(e -> extractorIds.contains(e.id()))
            .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<Widget>>> futures = new ArrayList<>();

            for (Resource resource : resources) {
                for (Extractor extractor : extractors) {
                    boolean compatible = extractor.compatibleTypes().stream()
                        .anyMatch(resource.types()::contains);
                    if (!compatible) continue;

                    futures.add(executor.submit(() -> fetchWidgets(domain, resource, extractor)));
                }
            }

            List<Widget> result = new ArrayList<>();
            for (Future<List<Widget>> future : futures) {
                result.addAll(future.get());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Widget> fetchWidgets(Domain domain, Resource resource, Extractor extractor) {
        try {
            List<ExtractorWidget> widgets = extractor.fetch(resource);
            return widgets.stream()
                .map(w -> new Widget(
                    resource.id() + "-" + extractor.id() + "-" + w.id(),
                    w.title(),
                    w.url(),
                    w.data(),
                    w.table(),
                    domain.id(),
                    resource.id(),
                    resource.name(),
                    extractor.id(),
                    extractor.name(),
                    null
                ))
                .toList();
        } catch (Exception e) {
            return List.of(new Widget(
                resource.id() + "-" + extractor.id() + "-error",
                extractor.name(),
                null, null, null,
                domain.id(), resource.id(), resource.name(),
                extractor.id(), extractor.name(),
                e.getMessage()
            ));
        }
    }
}
