package com.svp.tracker.fitness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared secret the Health Auto Export app sends. Blank token or user id turns the sync off. */
@ConfigurationProperties(prefix = "tracker.fitness")
public record AppleHealthSyncProperties(String appleHealthSyncToken, Long appleHealthSyncUserId) {

    public boolean enabled() {
        return appleHealthSyncToken != null
                && !appleHealthSyncToken.isBlank()
                && appleHealthSyncUserId != null
                && appleHealthSyncUserId > 0;
    }
}
