package com.svp.tracker.logging;

import java.util.List;

/** Lines that should not appear in the Logs UI (self-referential polling noise). */
public final class LogsApiPollNoise {

    private LogsApiPollNoise() {}

    /** True if this formatted log line is noise from logging the logs API itself (GET tail / poll). */
    public static boolean matchesLine(String formattedLine) {
        if (formattedLine == null || formattedLine.isBlank()) {
            return false;
        }
        return formattedLine.contains("GET /api/admin/logs");
    }

    /** True if this event message (formatted) is the same noise. */
    public static boolean matchesMessage(String formattedMessage) {
        if (formattedMessage == null || formattedMessage.isBlank()) {
            return false;
        }
        return formattedMessage.contains("GET /api/admin/logs");
    }

    public static List<String> filterLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines == null ? List.of() : lines;
        }
        return lines.stream().filter(l -> !matchesLine(l)).toList();
    }
}
