package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.banking.plaid")
public record BankingPlaidProperties(
        boolean enabled,
        String clientId,
        String secret,
        /**
         * Plaid API host selector: {@code sandbox}, {@code development}, or {@code production} (case-insensitive).
         */
        String environment,
        /**
         * Subdirectory under {@code tracker.finance.banking.import-directory} where Plaid-generated QFX files are
         * written (e.g. {@code plaid} → {@code .../imports/banking/plaid/...} on the host when import-directory is
         * {@code .../imports/banking}).
         */
        String outputSubdirectory) {

    public BankingPlaidProperties {
        if (clientId == null) {
            clientId = "";
        } else {
            clientId = clientId.trim();
        }
        if (secret == null) {
            secret = "";
        } else {
            secret = secret.trim();
        }
        if (environment == null || environment.isBlank()) {
            environment = "sandbox";
        } else {
            environment = environment.trim().toLowerCase();
        }
        if (outputSubdirectory == null || outputSubdirectory.isBlank()) {
            outputSubdirectory = "plaid";
        } else {
            outputSubdirectory = outputSubdirectory.trim().replaceAll("[/\\\\]+", "");
            if (outputSubdirectory.isBlank()) {
                outputSubdirectory = "plaid";
            }
        }
    }

    /** True when Plaid API calls are allowed (credentials present). */
    public boolean apiConfigured() {
        return enabled && !clientId.isEmpty() && !secret.isEmpty();
    }
}
