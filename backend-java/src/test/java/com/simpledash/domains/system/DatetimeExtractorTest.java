package com.simpledash.domains.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatetimeExtractorTest {

    @Test
    void returnsDateAndTime() throws Exception {
        var extractor = new DatetimeExtractor();
        var widgets = extractor.fetch(null);

        assertEquals(1, widgets.size());
        var widget = widgets.get(0);
        assertEquals("Date et heure", widget.title());
        assertTrue(widget.data().containsKey("Date"));
        assertTrue(widget.data().containsKey("Heure"));
    }
}
