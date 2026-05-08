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

    /**
     * Stable dedupe for OFX/QFX rows that carry a {@code <FITID>} (e.g. Plaid {@code transaction_id}). Prefer this over
     * {@link #transactionDedupeHex} when {@code fitId} is non-blank so pending→posted description changes do not insert
     * a second row on re-sync.
     */
    public static String transactionDedupeHexForOfxFit(long ownerUserId, long institutionId, String fitId) {
        if (fitId == null || fitId.isBlank()) {
            throw new IllegalArgumentException("fitId required for FIT-based dedupe");
        }
        String fid = fitId.trim();
        String payload = ownerUserId + "|" + institutionId + "|ofxfit|" + fid;
        return sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }
}

