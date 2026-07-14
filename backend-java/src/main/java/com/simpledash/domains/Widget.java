package com.simpledash.domains;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

// Forme finale exposée par l'API (une card côté front) : le registre décore
// l'ExtractorWidget brut avec les métadonnées de ressource/extracteur/domaine.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Widget(
    String id,
    String title,
    String url,
    Map<String, String> data,
    WidgetTable table,
    String domainId,
    String resourceId,
    String resourceName,
    String extractorId,
    String extractorName,
    String error
) {}
