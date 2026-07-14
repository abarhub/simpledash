package com.simpledash.domains.system;

import com.simpledash.domains.Extractor;
import com.simpledash.domains.ExtractorWidget;
import com.simpledash.domains.Resource;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SystemInfoExtractor implements Extractor {

    public String id() {
        return "system-info";
    }

    public String name() {
        return "Infos système";
    }

    public String description() {
        return "Mémoire, CPU et uptime";
    }

    public List<String> compatibleTypes() {
        return List.of("local");
    }

    public List<ExtractorWidget> fetch(Resource resource) {
        var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long total = osBean.getTotalMemorySize();
        long free = osBean.getFreeMemorySize();
        double load = osBean.getSystemLoadAverage();

        var memory = ExtractorWidget.data("memory", "Mémoire", Map.of(
            "Totale", formatBytes(total),
            "Utilisée", formatBytes(total - free),
            "Libre", formatBytes(free)
        ));

        var cpu = ExtractorWidget.data("cpu", "CPU", Map.of(
            "Cœurs", String.valueOf(Runtime.getRuntime().availableProcessors()),
            // getSystemLoadAverage() renvoie -1 si non supporté par l'OS (ex: Windows).
            "Charge (1 min)", load >= 0 ? String.format(Locale.ROOT, "%.2f", load) : "non disponible"
        ));

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        var uptime = ExtractorWidget.data("uptime", "Uptime", Map.of(
            // Pas d'équivalent standard multi-OS à l'uptime système en Java ;
            // seul l'uptime du process JVM est exposé ici.
            "Processus (JVM)", formatDuration(uptimeMs / 1000)
        ));

        return List.of(memory, cpu, uptime);
    }

    private static String formatBytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f Go", bytes / Math.pow(1024, 3));
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "min";
    }
}
