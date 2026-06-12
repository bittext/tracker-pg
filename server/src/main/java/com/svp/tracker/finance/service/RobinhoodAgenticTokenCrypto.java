package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
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

@Component
@Slf4j
public class RobinhoodAgenticTokenCrypto {

    public static final String SEAL_PREFIX = "enc1$";

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final RobinhoodAgenticProperties props;
    private final SecretKeySpec aesKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public RobinhoodAgenticTokenCrypto(RobinhoodAgenticProperties props) {
        this.props = props;
        byte[] raw = deriveAes256KeyBytes(props.tokenEncryptionKey());
        this.aesKey = raw == null ? null : new SecretKeySpec(raw, "AES");
    }

    @PostConstruct
    void warnIfEnabledWithoutEncryption() {
        if (props.enabled() && aesKey == null) {
            log.warn(
                    "Robinhood Agentic is enabled but {} is not set; OAuth tokens remain plaintext in the database.",
                    "TRACKER_FINANCE_ROBINHOOD_AGENTIC_TOKEN_ENCRYPTION_KEY");
        }
    }

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
            throw new IllegalStateException("Could not seal Robinhood Agentic token", e);
        }
    }

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
                throw new IllegalArgumentException("Truncated sealed token");
            }
            byte[] iv = Arrays.copyOfRange(packed, 0, GCM_IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(packed, GCM_IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not open sealed Robinhood Agentic token", e);
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
            // fall through
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(t.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
