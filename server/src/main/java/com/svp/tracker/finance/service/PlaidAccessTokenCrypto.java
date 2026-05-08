package com.svp.tracker.finance.service;

import com.svp.tracker.config.BankingPlaidProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Optional AES-256-GCM sealing for Plaid Item access tokens persisted in {@code banking_plaid_items.access_token}.
 * When {@link BankingPlaidProperties#accessTokenEncryptionKey()} is blank, values are stored unchanged (legacy behavior).
 */
@Component
@Slf4j
public class PlaidAccessTokenCrypto {

    /** Stored ciphertext prefix; plaintext legacy rows have no prefix. */
    public static final String SEAL_PREFIX = "enc1$";

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final BankingPlaidProperties plaidProps;
    private final SecretKeySpec aesKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PlaidAccessTokenCrypto(BankingPlaidProperties plaidProps) {
        this.plaidProps = plaidProps;
        byte[] raw = deriveAes256KeyBytes(plaidProps.accessTokenEncryptionKey());
        this.aesKey = raw == null ? null : new SecretKeySpec(raw, "AES");
    }

    @PostConstruct
    void warnIfPlaidApiWithoutEncryption() {
        if (plaidProps.enabled() && plaidProps.apiConfigured() && aesKey == null) {
            log.warn(
                    "Plaid API is enabled but {} is not set; Plaid access_token values remain stored as plaintext in the database. Set a strong key for encryption at rest.",
                    "TRACKER_PLAID_ACCESS_TOKEN_ENCRYPTION_KEY");
        }
    }

    public boolean isEnabled() {
        return aesKey != null;
    }

    /**
     * Seals a plaintext token for persistence. When encryption is disabled, returns {@code plaintext} unchanged.
     * Idempotent: values that already start with {@link #SEAL_PREFIX} are returned as-is.
     */
    public String seal(String plaintext) {
        if (aesKey == null || plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (plaintext.startsWith(SEAL_PREFIX)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return SEAL_PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal Plaid access token", e);
        }
    }

    /**
     * Opens a stored value for use with the Plaid API. When encryption is disabled, returns {@code stored} unchanged.
     * When encryption is enabled and {@code stored} has no seal prefix, treats the value as a legacy plaintext row
     * (returns it unchanged for this call; callers may persist a sealed copy).
     */
    public String open(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (aesKey == null || !stored.startsWith(SEAL_PREFIX)) {
            return stored;
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(SEAL_PREFIX.length()));
            if (packed.length < GCM_IV_LENGTH + 1) {
                throw new IllegalArgumentException("Truncated sealed Plaid token");
            }
            byte[] iv = Arrays.copyOfRange(packed, 0, GCM_IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(packed, GCM_IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not open sealed Plaid access token (wrong key or corrupt data)", e);
        }
    }

    private static byte[] deriveAes256KeyBytes(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String t = configured.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(t);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to passphrase digest
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(t.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
