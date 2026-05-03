package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional hardening toggles. HSTS should be enabled only when clients reach the app over HTTPS (TLS at the app or a
 * trusted reverse proxy with {@code server.forward-headers-strategy}).
 */
@ConfigurationProperties(prefix = "tracker.security")
public record SecurityProperties(boolean hstsEnabled) {

    public SecurityProperties {
        // YAML / relaxed binding supplies the flag; default false when absent.
    }
}
