package com.simpledash.domains.system;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DatetimeExtractor implements Extractor {

    public String id() {
        return "datetime";
    }

    public String name() {
        return "Date et heure";
    }

    public String description() {
        return "Date et heure actuelles du serveur";
    }

    public List<String> compatibleTypes() {
        return List.of("local");
    }

    public List<ExtractorWidget> fetch(Resource resource) {
        var now = LocalDateTime.now();
        var dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
        var timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE);

        return List.of(ExtractorWidget.data("now", "Date et heure", Map.of(
            "Date", now.format(dateFmt),
            "Heure", now.format(timeFmt)
        )));
    }
}
