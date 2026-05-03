package com.svp.tracker.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.web")
public record WebProperties(List<String> corsAllowedOriginPatterns, String publicAppUrl) {

    public WebProperties {
        if (corsAllowedOriginPatterns == null || corsAllowedOriginPatterns.isEmpty()) {
            corsAllowedOriginPatterns = List.of(
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "http://[::1]:*");
        }
        if (publicAppUrl == null || publicAppUrl.isBlank()) {
            publicAppUrl = "http://localhost:4200";
        } else {
            publicAppUrl = publicAppUrl.trim();
            while (publicAppUrl.endsWith("/")) {
                publicAppUrl = publicAppUrl.substring(0, publicAppUrl.length() - 1);
            }
        }
    }

    /** Mutable copy for Spring MVC CORS registration. */
    public List<String> corsPatterns() {
        return new ArrayList<>(corsAllowedOriginPatterns);
    }
}
