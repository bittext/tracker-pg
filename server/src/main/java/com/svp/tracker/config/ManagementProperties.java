package com.svp.tracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tracker.management")
public class ManagementProperties {

    /** Directory for Day One attachment files; empty = java.io.tmpdir/tracker-day-one */
    private String dayOneStorageDirectory = "";

    /** Max upload size per file (bytes). */
    private long dayOneMaxAttachmentBytes = 8L * 1024 * 1024;
}
