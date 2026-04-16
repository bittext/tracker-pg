package com.svp.tracker.logging;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Drops in-memory log lines whose leading timestamp is older than a cutoff, matching {@link
 * MemoryLogAppender}'s {@code %d{yyyy-MM-dd HH:mm:ss.SSS}} prefix. Lines without a parseable prefix are kept.
 */
public final class LogWebDisplayFilter {

    /** Length of {@code yyyy-MM-dd HH:mm:ss.SSS} prefix. */
    private static final int TS_PREFIX_LEN = 23;

    private static final DateTimeFormatter PREFIX_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private LogWebDisplayFilter() {}

    /**
     * @param maxAgeMinutes if {@code <= 0}, returns {@code lines} unchanged (no age filter).
     */
    public static List<String> keepWithinLastMinutes(List<String> lines, int maxAgeMinutes) {
        if (lines == null || lines.isEmpty() || maxAgeMinutes <= 0) {
            return lines == null ? List.of() : lines;
        }
        Instant cutoff = Instant.now().minus(maxAgeMinutes, ChronoUnit.MINUTES);
        ZoneId zone = ZoneId.systemDefault();
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (keepLine(line, cutoff, zone)) {
                out.add(line);
            }
        }
        return out;
    }

    private static boolean keepLine(String line, Instant cutoff, ZoneId zone) {
        if (line == null || line.length() < TS_PREFIX_LEN) {
            return true;
        }
        String prefix = line.substring(0, TS_PREFIX_LEN);
        try {
            LocalDateTime ldt = LocalDateTime.parse(prefix, PREFIX_TS);
            Instant lineInstant = ldt.atZone(zone).toInstant();
            return !lineInstant.isBefore(cutoff);
        } catch (DateTimeParseException e) {
            return true;
        }
    }
}
