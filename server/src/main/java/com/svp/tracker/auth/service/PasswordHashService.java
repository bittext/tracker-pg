package com.svp.tracker.auth.service;

import com.svp.tracker.auth.config.AuthProperties;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * One-way password protection: BCrypt over a per-user random salt and application-level pepper. Raw passwords are
 * never persisted and cannot be recovered from stored values.
 */
@Service
public class PasswordHashService {

    private final AuthProperties authProperties;
    private final BCryptPasswordEncoder encoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHashService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.encoder = new BCryptPasswordEncoder(authProperties.bcryptStrength());
    }

    public PasswordHash create(String rawPassword) {
        String salt = randomSalt();
        String hashed = encoder.encode(peppered(rawPassword, salt));
        return new PasswordHash(hashed, salt);
    }

    public boolean verify(String rawPassword, String encodedHash, String salt) {
        if (rawPassword == null || encodedHash == null || salt == null) {
            return false;
        }
        return encoder.matches(peppered(rawPassword, salt), encodedHash);
    }

    private String peppered(String rawPassword, String salt) {
        return rawPassword + "::" + salt + "::" + authProperties.passwordPepper();
    }

    private String randomSalt() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record PasswordHash(String hash, String salt) {}
}
