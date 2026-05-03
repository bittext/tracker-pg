package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.github")
public record GithubProperties(boolean enabled, String owner, String repo, String apiToken) {

    public GithubProperties {
        owner = owner == null ? "" : owner.trim();
        repo = repo == null ? "" : repo.trim();
        apiToken = apiToken == null ? "" : apiToken.trim();
    }

    public boolean configured() {
        return enabled && !owner.isEmpty() && !repo.isEmpty();
    }
}
