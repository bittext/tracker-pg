package com.svp.tracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tracker.journal")
public class JournalProperties {

    /** Directory for attachment files; empty = java.io.tmpdir/tracker-journal */
    private String storageDirectory = "";

    private long maxAttachmentBytes = 8L * 1024 * 1024;
}
