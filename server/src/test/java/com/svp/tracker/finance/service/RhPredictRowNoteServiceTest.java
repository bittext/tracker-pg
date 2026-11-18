package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RhPredictRowNoteServiceTest {

    @Test
    void acceptsDayAndCloseKeysForAJourney() {
        assertEquals("predict-fresh-v1:day:2026-11-17", RhPredictRowNoteService.requireKey("predict-fresh-v1:day:2026-11-17"));
        assertEquals("predict-restart-v1:close:123", RhPredictRowNoteService.requireKey(" predict-restart-v1:close:123 "));
    }

    @Test
    void rejectsKeysThatAreNotARow() {
        for (String bad : new String[] {null, "", "day:2026-11-17", "x:day:not-a-date", "x:close:abc", "x:other:1", "X:day:2026-11-17"}) {
            assertThrows(ResponseStatusException.class, () -> RhPredictRowNoteService.requireKey(bad), String.valueOf(bad));
        }
    }

    @Test
    void cleanCollapsesWhitespaceAndCapsLength() {
        assertEquals("", RhPredictRowNoteService.clean(null));
        assertEquals("", RhPredictRowNoteService.clean("   \n "));
        assertEquals("took profit early", RhPredictRowNoteService.clean("  took   profit\nearly  "));
        assertEquals(RhPredictRowNoteService.MAX_BODY, RhPredictRowNoteService.clean("a".repeat(500)).length());
    }
}
