package com.svp.tracker.finance.service.banking;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public final class BankingHashUtil {

    private BankingHashUtil() {}

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String transactionDedupeHex(
            long ownerUserId, long institutionId, LocalDate date, BigDecimal amount, String description) {
        String norm = description == null ? "" : description.trim().replaceAll("\\s+", " ");
        String payload = ownerUserId
                + "|"
                + institutionId
                + "|"
                + date
                + "|"
                + amount.stripTrailingZeros().toPlainString()
                + "|"
                + norm.toLowerCase();
        return sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }
}
