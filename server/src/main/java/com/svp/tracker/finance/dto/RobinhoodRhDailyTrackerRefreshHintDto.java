package com.svp.tracker.finance.dto;

import java.time.Instant;

/** Lightweight poll target so the Daily Tracker UI can refresh when new snapshots land. */
public record RobinhoodRhDailyTrackerRefreshHintDto(
        Instant latestSnapshotAt,
        long latestSnapshotId,
        String latestCaptureKind) {}
