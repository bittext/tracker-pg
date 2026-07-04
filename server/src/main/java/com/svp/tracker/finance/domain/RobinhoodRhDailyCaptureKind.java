package com.svp.tracker.finance.domain;

public final class RobinhoodRhDailyCaptureKind {

    public static final String SCHEDULED = "SCHEDULED";
    /** Hourly auto-capture rows (timeline); one row per pull per account. */
    public static final String INTRADAY = "INTRADAY";
    public static final String MANUAL = "MANUAL";

    private RobinhoodRhDailyCaptureKind() {}
}
