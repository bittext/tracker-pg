package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FinanceNewsScanServiceTest {

    @Test
    void parseTickersSplitsCommaSpaceAndNewline() {
        assertEquals(List.of("HOOD", "MRNA", "SKHY"), FinanceNewsScanService.parseTickers("HOOD, MRNA\nSKHY"));
    }

    @Test
    void parseTickersDedupesAndUppercases() {
        assertEquals(List.of("HOOD", "MRNA"), FinanceNewsScanService.parseTickers("hood hood mrna"));
    }

    @Test
    void parseTickersRejectsInvalidSymbol() {
        assertThrows(ResponseStatusException.class, () -> FinanceNewsScanService.parseTickers("HOOD, 1234"));
    }

    @Test
    void parseTickersCapsAtTwelve() {
        assertThrows(
                ResponseStatusException.class,
                () -> FinanceNewsScanService.parseTickers("A B C D E F G H I J K L M"));
    }

    @Test
    void publishedWithinKeepsLast36Hours() {
        Instant now = Instant.parse("2026-09-20T03:00:00Z");
        assertTrue(FinanceNewsScanService.publishedWithin("2026-09-19T00:00:00Z", now, Duration.ofHours(36)));
        assertFalse(FinanceNewsScanService.publishedWithin("2026-09-18T00:00:00Z", now, Duration.ofHours(36)));
        assertTrue(FinanceNewsScanService.publishedWithin("", now, Duration.ofHours(36)));
    }
}
