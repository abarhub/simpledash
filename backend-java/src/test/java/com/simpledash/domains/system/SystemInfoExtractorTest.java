package com.simpledash.domains.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SystemInfoExtractorTest {

    @Test
    void returnsMemoryCpuAndUptimeWidgets() throws Exception {
        var extractor = new SystemInfoExtractor();
        var widgets = extractor.fetch(null);

        assertEquals(3, widgets.size());
        assertEquals("Mémoire", widgets.get(0).title());
        assertEquals("CPU", widgets.get(1).title());
        assertEquals("Uptime", widgets.get(2).title());
    }
}
