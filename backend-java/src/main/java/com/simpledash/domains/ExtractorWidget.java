package com.simpledash.domains;

import java.util.Map;

// Sortie brute d'un extracteur, avant que le registre ne la décore avec les
// métadonnées de ressource/extracteur/domaine (voir Widget).
public record ExtractorWidget(String id, String title, String url, Map<String, String> data, WidgetTable table) {

    public static ExtractorWidget data(String id, String title, Map<String, String> data) {
        return new ExtractorWidget(id, title, null, data, null);
    }

    public static ExtractorWidget data(String id, String title, String url, Map<String, String> data) {
        return new ExtractorWidget(id, title, url, data, null);
    }

    public static ExtractorWidget table(String id, String title, WidgetTable table) {
        return new ExtractorWidget(id, title, null, null, table);
    }

    public static ExtractorWidget table(String id, String title, String url, WidgetTable table) {
        return new ExtractorWidget(id, title, url, null, table);
    }
}
