package com.svp.tracker.auth.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.auth")
public record AuthProperties(
        String jwtSecret,
        int jwtTtlSeconds,
        String passwordPepper,
        /** BCrypt work factor (4–31). Higher is slower and more resistant to offline guessing. */
        int bcryptStrength,
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
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "tracker.auth.jwt-secret must be at least 32 UTF-8 bytes (JJWT HS256 requirement); "
                            + "fix application.yml or application-local.yml");
        }
        if (jwtTtlSeconds < 300) {
            jwtTtlSeconds = 43_200;
        }
        if (passwordPepper == null) {
            passwordPepper = "";
        }
        if (bcryptStrength < 4) {
            bcryptStrength = 12;
        }
        if (bcryptStrength > 31) {
            bcryptStrength = 31;
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
