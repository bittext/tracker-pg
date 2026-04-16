package com.svp.tracker.logging;

import java.util.ArrayList;
import java.util.List;

/** Thread-safe ring buffer of formatted log lines (Logback → {@link MemoryLogAppender}). */
public final class LogLineBuffer {

    private static final List<String> LINES = new ArrayList<>(1024);
    private static volatile int capacity = 500;

    private LogLineBuffer() {}

    public static synchronized void setCapacity(int max) {
        capacity = Math.max(50, Math.min(max, 5_000));
        while (LINES.size() > capacity) {
            LINES.remove(0);
        }
    }

    public static synchronized void addLine(String line) {
        LINES.add(line);
        while (LINES.size() > capacity) {
            LINES.remove(0);
        }
    }

    /** Last {@code limit} lines, oldest first. */
    public static synchronized List<String> tail(int limit) {
        if (LINES.isEmpty()) {
            return List.of();
        }
        int n = Math.min(Math.max(1, limit), LINES.size());
        return new ArrayList<>(LINES.subList(LINES.size() - n, LINES.size()));
    }

    public static synchronized int bufferedSize() {
        return LINES.size();
    }
}
