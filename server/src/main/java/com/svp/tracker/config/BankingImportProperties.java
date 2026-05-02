package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.banking")
public record BankingImportProperties(
        /**
         * Absolute directory for saved banking uploads (CSV, QFX, PDF, etc.). Subfolders per user and institution are
         * created under this root. Empty disables imports until configured.
         */
        String importDirectory,
        /** Max bytes per uploaded banking file. */
        int maxUploadBytes) {

    public BankingImportProperties {
        if (importDirectory == null) {
            importDirectory = "";
        } else {
            importDirectory = importDirectory.trim();
        }
        if (maxUploadBytes < 1024) {
            maxUploadBytes = 20_971_520; // 20 MiB
        }
        if (maxUploadBytes > 52_428_800) {
            maxUploadBytes = 52_428_800; // 50 MiB cap
        }
    }
}
