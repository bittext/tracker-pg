package com.svp.tracker.finance.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Classifies Robinhood CSV cash movements (deposits, withdrawals, transfers) vs trade legs. */
final class RobinhoodCashFlowClassifier {

    private static final Set<String> TRADE_CODES =
            Set.of("BTO", "BTC", "BUY", "STC", "STO", "SELL", "OEXP", "OASGN", "OEXCS", "CONV", "SPL", "MRGS", "SXCH");

    private static final Set<String> TRANSFER_CODES =
            Set.of(
                    "ACH",
                    "XENT",
                    "ITRF",
                    "RTP",
                    "CDEP",
                    "CSR",
                    "WIRE",
                    "UKBT",
                    "DCNT",
                    "TRF",
                    "TRFI",
                    "TRFO");

    private static final Set<String> CASH_IN_CODES = Set.of("INT", "MINT", "SLIP", "CSH");

    private static final Set<String> CASH_OUT_CODES = Set.of("FEE", "GOLD", "RGFE", "ACAT", "XENT_CC");

    private RobinhoodCashFlowClassifier() {}

    static boolean isCashFlowRow(String transCode, String description, String instrument) {
        String code = codeKey(transCode);
        if (!code.isEmpty() && TRADE_CODES.contains(code)) {
            return false;
        }
        if (isTransferFamilyCode(code)) {
            return true;
        }
        if (!code.isEmpty() && (CASH_IN_CODES.contains(code) || CASH_OUT_CODES.contains(code))) {
            return true;
        }
        if (!code.isEmpty() && code.startsWith("ACH")) {
            return true;
        }
        String desc = norm(description);
        if (desc.isEmpty()) {
            return blank(instrument) && !code.isEmpty() && !TRADE_CODES.contains(code);
        }
        if (desc.contains("TRANSFER")) {
            return true;
        }
        if (desc.contains("DEPOSIT")
                || desc.contains("WITHDRAW")
                || desc.contains("INSTANT BANK")
                || desc.contains("WIRE ")
                || desc.contains("ACH ")) {
            return true;
        }
        if (desc.contains("CREDIT CARD")) {
            return true;
        }
        return false;
    }

    static boolean isTransferFamilyCode(String codeKey) {
        if (codeKey.isEmpty()) {
            return false;
        }
        if (TRANSFER_CODES.contains(codeKey)) {
            return true;
        }
        if (codeKey.startsWith("ACH")) {
            return true;
        }
        return codeKey.equals("TRANSFER")
                || codeKey.startsWith("TRANSFERIN")
                || codeKey.startsWith("TRANSFEROUT")
                || codeKey.endsWith("TRANSFERIN")
                || codeKey.endsWith("TRANSFEROUT");
    }

    /** {@code IN} = money into the account; {@code OUT} = money out. */
    static String cashFlowDirection(String transCode, String description, BigDecimal amount) {
        String code = codeKey(transCode);
        if (isInternalTransfer(transCode, description)) {
            if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
                return "OUT";
            }
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return "IN";
            }
            Optional<String> explicit = explicitDirection(code, norm(description));
            return explicit.orElse("OTHER");
        }
        Optional<String> explicit = explicitDirection(code, norm(description));
        if (explicit.isPresent()) {
            return explicit.get();
        }
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            return "OUT";
        }
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            return "IN";
        }
        return "OTHER";
    }

    static String flowCategory(String transCode, String description, String direction) {
        String code = codeKey(transCode);
        if ("STARTING_BALANCE".equalsIgnoreCase(trimOrNull(transCode))) {
            return "STARTING_BALANCE";
        }
        if (isInternalTransfer(transCode, description)) {
            return "IN".equals(direction) ? "INTERNAL_IN" : "INTERNAL_OUT";
        }
        if (CASH_IN_CODES.contains(code)) {
            return "INTEREST";
        }
        if (CASH_OUT_CODES.contains(code)) {
            return "FEE";
        }
        return "IN".equals(direction) ? "EXTERNAL_IN" : "EXTERNAL_OUT";
    }

    static boolean isInternalTransfer(String transCode, String description) {
        String code = codeKey(transCode);
        if ("ITRF".equals(code)) {
            return true;
        }
        String desc = norm(description);
        if (desc.isEmpty()) {
            return false;
        }
        if (isExternalBankTransfer(desc)) {
            return false;
        }
        if (desc.contains("INTERNAL TRANSFER") || desc.contains("TRANSFER TO") || desc.contains("TRANSFER FROM")) {
            return true;
        }
        if (desc.contains("TRANSFER FROM BROKERAGE TO BROKERAGE")) {
            return true;
        }
        if (desc.contains("TRANSFER") && (desc.contains("AGENTIC") || desc.contains("MANAGED") || desc.contains("ACCOUNT"))) {
            return true;
        }
        if (SUFFIX_IN_TEXT.matcher(description).find() && desc.contains("TRANSFER")) {
            return true;
        }
        return false;
    }

    private static final java.util.regex.Pattern SUFFIX_IN_TEXT =
            java.util.regex.Pattern.compile(
                    "(?:ENDING\\s+IN|••••|\\bACCOUNT\\b\\s*(?:ENDING\\s+IN)?\\s*)(\\d{4})\\b",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private static boolean isExternalBankTransfer(String desc) {
        return desc.contains("BANK")
                || desc.contains(" ACH")
                || desc.startsWith("ACH")
                || desc.contains("INSTANT BANK")
                || desc.contains("WIRE")
                || desc.contains("DEBIT CARD")
                || desc.contains("RTP");
    }

    static boolean internalTransfer(String transCode, String description) {
        return isInternalTransfer(transCode, description);
    }

    static String codeKey(String transCode) {
        if (transCode == null) {
            return "";
        }
        return norm(transCode).replace(" ", "").replace("-", "").replace("_", "");
    }

    static String displayFlowType(String transCode, String description) {
        String code = norm(transCode);
        if (!code.isEmpty()) {
            return code;
        }
        String desc = trimOrNull(description);
        return desc != null ? desc : "Cash flow";
    }

    private static Optional<String> explicitDirection(String codeKey, String description) {
        if (codeKey.startsWith("TRANSFERIN") || codeKey.endsWith("TRANSFERIN") || codeKey.equals("TRFI")) {
            return Optional.of("IN");
        }
        if (codeKey.startsWith("TRANSFEROUT") || codeKey.endsWith("TRANSFEROUT") || codeKey.equals("TRFO")) {
            return Optional.of("OUT");
        }
        if (codeKey.equals("CDEP") || codeKey.equals("RTP")) {
            return Optional.empty();
        }
        if (CASH_OUT_CODES.contains(codeKey)) {
            return Optional.of("OUT");
        }
        if (description.contains("CREDIT CARD BALANCE PAYMENT") || description.contains("ROBINHOOD CREDIT CARD")) {
            return Optional.of("OUT");
        }
        if (description.contains("TRANSFER IN") || description.contains("DEPOSIT")) {
            return Optional.of("IN");
        }
        if (description.contains("TRANSFER OUT") || description.contains("WITHDRAW")) {
            return Optional.of("OUT");
        }
        if (codeKey.equals("TRANSFER")) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
