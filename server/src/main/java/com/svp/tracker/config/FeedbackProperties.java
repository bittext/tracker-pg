package com.svp.tracker.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.feedback")
public record FeedbackProperties(
        /**
         * When non-empty, Contact us sends only to these addresses (comma-separated). Overrides ADMIN profile
         * emails — use in production so placeholder profile emails are not notified.
         */
        String adminEmails,
        /** Additional inboxes when {@link #adminEmails} is empty and ADMIN accounts lack profile email. */
        String fallbackAdminEmails,
        /** Never notify these addresses (comma-separated), even from ADMIN profiles or fallback. */
        String excludedAdminEmails) {

    public FeedbackProperties {
        adminEmails = adminEmails == null ? "" : adminEmails.trim();
        fallbackAdminEmails = fallbackAdminEmails == null ? "" : fallbackAdminEmails.trim();
        excludedAdminEmails = excludedAdminEmails == null ? "" : excludedAdminEmails.trim();
    }

    /** Explicit allowlist; empty means derive from ADMIN member profiles + {@link #fallbackEmailList()}. */
    public List<String> adminEmailList() {
        return FeedbackEmailAddresses.validOnly(FeedbackEmailAddresses.parseCommaList(adminEmails));
    }

    public List<String> fallbackEmailList() {
        return FeedbackEmailAddresses.validOnly(FeedbackEmailAddresses.parseCommaList(fallbackAdminEmails));
    }

    public List<String> excludedEmailList() {
        return FeedbackEmailAddresses.validOnly(FeedbackEmailAddresses.parseCommaList(excludedAdminEmails));
    }
}
