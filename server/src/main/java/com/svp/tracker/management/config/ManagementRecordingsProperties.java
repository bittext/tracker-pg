package com.svp.tracker.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Management → Recordings: cloud-backed Just Press Record library. Audio is uploaded from the user's device
 * (typically the iCloud Drive folder synced by Just Press Record) into journal blob storage (local dir or S3).
 */
@ConfigurationProperties(prefix = "tracker.management.recordings")
public record ManagementRecordingsProperties(boolean enabled, boolean autoProcess) {

    public boolean configured() {
        return enabled;
    }

    /** When true, new uploads (and cleared old transcripts) are queued for Whisper + summary. */
    public boolean autoProcessEnabled() {
        return enabled && autoProcess;
    }
}
