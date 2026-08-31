package com.svp.tracker.life.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LifeMonthNoteServiceTest {

    @Test
    void stripAttachmentEmbedsRemovesHtmlAndMarkdown() {
        String body =
                "Hello\n<img src=\"/api/life/notes/attachments/42/file\" alt=\"x\" />\n\n"
                        + "![x](/api/life/notes/attachments/42/file)\nKeep 41 "
                        + "<img src=\"/api/life/notes/attachments/41/file\" />";
        String out = LifeMonthNoteService.stripAttachmentEmbedsFromBody(body, 42);
        assertFalse(out.contains("/attachments/42/file"));
        assertTrue(out.contains("Hello"));
        assertTrue(out.contains("Keep 41"));
        assertTrue(out.contains("/attachments/41/file"));
    }

    @Test
    void stripAttachmentEmbedsEmptyBody() {
        assertEquals("", LifeMonthNoteService.stripAttachmentEmbedsFromBody(null, 1));
        assertEquals("", LifeMonthNoteService.stripAttachmentEmbedsFromBody("", 1));
    }
}
