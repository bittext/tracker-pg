package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

/** Stable option contract identity for Daily Tracker holdings / ownership history. */
final class RobinhoodRhContractKeys {

    private RobinhoodRhContractKeys() {}

    static boolean isOption(RobinhoodRhHoldingDto h) {
        if (h == null) {
            return false;
        }
        String type = h.positionType() == null ? "" : h.positionType().trim().toLowerCase(Locale.ROOT);
        return "option".equals(type);
    }

    /**
     * Prefer enriched metadata / positionKey; otherwise a legacy fingerprint from chain + avg buy so
     * historical snapshots (pre-enrichment) still separate distinct lots.
     */
    static String contractKeyForHolding(RobinhoodRhHoldingDto h) {
        if (h == null || !isOption(h)) {
            return "";
        }
        String chain = chainSymbol(h);
        if (hasFullIdentity(h)) {
            String type = h.optionType().trim().toLowerCase(Locale.ROOT);
            String strike = h.strikePrice().stripTrailingZeros().toPlainString();
            String exp = h.expirationDate().toString();
            return chain + "|" + type + "|" + strike + "|" + exp;
        }
        if (h.positionKey() != null && !h.positionKey().isBlank()) {
            String pk = h.positionKey().trim();
            // Prefer human-stable chain|type|strike|exp form when sync stored that shape.
            if (looksLikeContractDescriptor(pk)) {
                return pk.toUpperCase(Locale.ROOT);
            }
            return "PK|" + pk;
        }
        BigDecimal avg = h.averageBuyPrice() == null
                ? BigDecimal.ZERO
                : h.averageBuyPrice().setScale(2, RoundingMode.HALF_UP);
        return "LEGACY|" + chain + "|" + avg.stripTrailingZeros().toPlainString();
    }

    static String contractLabel(RobinhoodRhHoldingDto h) {
        if (h == null || !isOption(h)) {
            return "";
        }
        String chain = chainSymbol(h);
        if (hasFullIdentity(h)) {
            String type = h.optionType().trim().toUpperCase(Locale.ROOT);
            String strike = h.strikePrice().stripTrailingZeros().toPlainString();
            return chain + " " + type + " " + strike + " · " + h.expirationDate();
        }
        BigDecimal avg = h.averageBuyPrice();
        if (avg != null && avg.signum() > 0) {
            return chain + " option @ $" + avg.setScale(2, RoundingMode.HALF_UP).toPlainString() + " avg";
        }
        return chain + " option";
    }

    static boolean isLegacyIdentity(RobinhoodRhHoldingDto h) {
        return isOption(h) && !hasFullIdentity(h);
    }

    static String chainSymbol(RobinhoodRhHoldingDto h) {
        if (h == null) {
            return "";
        }
        if (h.chainSymbol() != null && !h.chainSymbol().isBlank()) {
            return h.chainSymbol().trim().toUpperCase(Locale.ROOT);
        }
        return h.symbol() == null ? "" : h.symbol().trim().toUpperCase(Locale.ROOT);
    }

    static String optionType(RobinhoodRhHoldingDto h) {
        if (h == null || h.optionType() == null || h.optionType().isBlank()) {
            return null;
        }
        return h.optionType().trim().toLowerCase(Locale.ROOT);
    }

    static BigDecimal strikePrice(RobinhoodRhHoldingDto h) {
        return h == null ? null : h.strikePrice();
    }

    static LocalDate expirationDate(RobinhoodRhHoldingDto h) {
        return h == null ? null : h.expirationDate();
    }

    private static boolean hasFullIdentity(RobinhoodRhHoldingDto h) {
        return h.optionType() != null
                && !h.optionType().isBlank()
                && h.strikePrice() != null
                && h.expirationDate() != null;
    }

    private static boolean looksLikeContractDescriptor(String positionKey) {
        String[] parts = positionKey.split("\\|");
        if (parts.length < 4) {
            return false;
        }
        String type = parts[1].trim().toLowerCase(Locale.ROOT);
        return "call".equals(type) || "put".equals(type);
    }
}
