package com.svp.tracker.management.dto;

public record ManagementAccountDto(
        long id,
        String itemName,
        String folder,
        String username,
        /** Decrypted plaintext for the signed-in owner. Server never returns sealed ciphertext. */
        String password,
        /** Decrypted plaintext authenticator key (e.g. TOTP secret). */
        String authenticatorKey,
        String website,
        String notes,
        String createdAt,
        String updatedAt) {}
