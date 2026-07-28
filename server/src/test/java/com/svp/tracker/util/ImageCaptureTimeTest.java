package com.svp.tracker.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ImageCaptureTimeTest {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");

    @Test
    void exifLocalWallClockUsesCentralWhenNoOffset() {
        Instant got = ImageCaptureTime.parseExifDateTimeString("2026:07:27 21:13:00", null);
        assertNotNull(got);
        Instant expected = LocalDateTime.of(2026, 7, 27, 21, 13, 0).atZone(CENTRAL).toInstant();
        assertEquals(expected, got);
    }

    @Test
    void exifOffsetIsHonored() {
        Instant got = ImageCaptureTime.parseExifDateTimeString("2026:07:27 21:13:00", "-05:00");
        assertNotNull(got);
        assertEquals(LocalDateTime.of(2026, 7, 27, 21, 13, 0).atOffset(ZoneOffset.of("-05:00")).toInstant(), got);
    }

    @Test
    void filenameScreenshotEvening() {
        Instant got = ImageCaptureTime.fromFilename("Screenshot 2026-07-27 at 9.13.00 PM.png");
        assertNotNull(got);
        Instant expected = LocalDateTime.of(2026, 7, 27, 21, 13, 0).atZone(CENTRAL).toInstant();
        assertEquals(expected, got);
    }

    @Test
    void clientLastModifiedUsedWhenNoMeta() {
        Instant got = ImageCaptureTime.resolve("notes.pdf", "application/pdf", new byte[] {1, 2, 3}, 1_700_000_000_000L);
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), got);
    }

    @Test
    void blankReturnsNull() {
        assertNull(ImageCaptureTime.parseExifDateTimeString(" ", null));
        assertNull(ImageCaptureTime.fromFilename("random.png"));
    }
}
