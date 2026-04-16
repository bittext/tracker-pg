package com.svp.tracker.auth.service;

import com.svp.tracker.auth.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class LocationFingerprintService {

    private final AuthProperties authProperties;

    public LocationFingerprintService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String fingerprint(String remoteIp, String userAgent, String locationSource) {
        String raw = normalize(remoteIp) + "|" + normalize(userAgent) + "|" + normalize(locationSource) + "|"
                + authProperties.passwordPepper();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim().toLowerCase();
    }
}
