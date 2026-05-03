package com.svp.tracker.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.feedback")
public record FeedbackProperties(String fallbackAdminEmails) {

    public FeedbackProperties {
        fallbackAdminEmails = fallbackAdminEmails == null ? "" : fallbackAdminEmails.trim();
    }

    /** Additional or substitute inboxes when ADMIN accounts have no member profile email (comma-separated). */
    public List<String> fallbackEmailList() {
        if (fallbackAdminEmails.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fallbackAdminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.contains("@"))
                .distinct()
                .toList();
    }
}
