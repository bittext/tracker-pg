package com.svp.tracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tracker.journal")
public class JournalProperties {

    /** Directory for attachment files; empty = java.io.tmpdir/tracker-journal (used when S3 bucket is not set) */
    private String storageDirectory = "";

    private long maxAttachmentBytes = 8L * 1024 * 1024;

    /**
     * If non-empty, journal attachments are stored in this S3 bucket (e.g. tracker-pg-journal) using the
     * default AWS credentials chain. Otherwise the local storage-directory is used.
     */
    private String s3Bucket = "";

    /** AWS region for the S3 client (e.g. us-east-1). */
    private String s3Region = "us-east-1";
}
