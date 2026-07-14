package com.simpledash.domains.servers;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerInfoExtractor implements Extractor {

    public String id() {
        return "server-info";
    }

    public String name() {
        return "Infos configurées";
    }

    public String description() {
        return "Métadonnées déclarées dans la config (sans appel réseau)";
    }

    public List<String> compatibleTypes() {
        return List.of("http", "ssh");
    }

    public List<ExtractorWidget> fetch(Resource resource) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("Type", String.join(", ", resource.types()));

        Object url = resource.get("url");
        if (url != null) data.put("URL", (String) url);

        Object host = resource.get("host");
        if (host != null) data.put("Host", (String) host);

        return List.of(ExtractorWidget.data("info", "Infos", data));
    }
}
