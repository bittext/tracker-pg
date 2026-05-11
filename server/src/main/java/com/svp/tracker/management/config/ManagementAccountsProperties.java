package com.svp.tracker.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.management.accounts")
public record ManagementAccountsProperties(
        /**
         * When non-blank, {@code password} and {@code authenticator_key} values for Management → Account are sealed
         * at rest with AES-256-GCM. Use a long random passphrase (SHA-256–derived key) or Base64-encoded 32 raw bytes.
         * Leave blank only for local/dev where plaintext at rest is acceptable.
         */
        String encryptionKey) {

    public ManagementAccountsProperties {
        if (encryptionKey == null) {
            encryptionKey = "";
        } else {
            encryptionKey = encryptionKey.trim();
        }
    }
}
