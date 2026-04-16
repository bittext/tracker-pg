package com.svp.tracker.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.auth")
public record AuthProperties(
        String jwtSecret,
        int jwtTtlSeconds,
        String passwordPepper,
        int trustedLocationDays,
        int mfaOtpTtlSeconds,
        int mfaMaxAttempts,
        int mfaRateLimitPerHour,
        boolean bootstrapAdminEnabled,
        String bootstrapAdminUsername,
        String bootstrapAdminPassword) {

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = "dev-secret-change-me-dev-secret-change-me-dev-secret-change-me";
        }
        if (jwtTtlSeconds < 300) {
            jwtTtlSeconds = 43_200;
        }
        if (passwordPepper == null) {
            passwordPepper = "";
        }
        if (trustedLocationDays < 1) {
            trustedLocationDays = 90;
        }
        if (mfaOtpTtlSeconds < 60) {
            mfaOtpTtlSeconds = 300;
        }
        if (mfaMaxAttempts < 1) {
            mfaMaxAttempts = 5;
        }
        if (mfaRateLimitPerHour < 1) {
            mfaRateLimitPerHour = 12;
        }
        if (bootstrapAdminUsername == null || bootstrapAdminUsername.isBlank()) {
            bootstrapAdminUsername = "admin";
        }
        if (bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
            bootstrapAdminPassword = "admin123";
        }
    }

    public Duration jwtTtl() {
        return Duration.ofSeconds(jwtTtlSeconds);
    }
}
