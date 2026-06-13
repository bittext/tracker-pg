package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.FinanceLoanOptionDto;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FinanceLoanCatalog {

    public enum LoanNature {
        MORTGAGE("Mortgage"),
        HOME_EQUITY("Home equity"),
        AUTO("Auto"),
        PERSONAL("Personal"),
        STUDENT("Student"),
        BUSINESS("Business"),
        LINE_OF_CREDIT("Line of credit"),
        CREDIT_CARD("Credit card"),
        MEDICAL("Medical"),
        OTHER("Other");

        private final String label;

        LoanNature(String label) {
            this.label = label;
        }

        public String wire() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    public enum PaymentFrequency {
        WEEKLY("Weekly"),
        BIWEEKLY("Bi-weekly"),
        MONTHLY("Monthly"),
        QUARTERLY("Quarterly"),
        SEMI_ANNUAL("Semi-annual"),
        ANNUAL("Annual"),
        ONE_TIME("One-time / lump sum"),
        OTHER("Other");

        private final String label;

        PaymentFrequency(String label) {
            this.label = label;
        }

        public String wire() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    private static final Map<String, String> NATURE_LABELS =
            Arrays.stream(LoanNature.values()).collect(Collectors.toMap(LoanNature::wire, LoanNature::label));

    private static final Map<String, String> FREQUENCY_LABELS = Arrays.stream(PaymentFrequency.values())
            .collect(Collectors.toMap(PaymentFrequency::wire, PaymentFrequency::label));

    private FinanceLoanCatalog() {}

    public static List<FinanceLoanOptionDto> loanNatureOptions() {
        return Arrays.stream(LoanNature.values())
                .map(n -> new FinanceLoanOptionDto(n.wire(), n.label()))
                .toList();
    }

    public static List<FinanceLoanOptionDto> paymentFrequencyOptions() {
        return Arrays.stream(PaymentFrequency.values())
                .map(f -> new FinanceLoanOptionDto(f.wire(), f.label()))
                .toList();
    }

    public static String natureLabel(String wire) {
        if (wire == null || wire.isBlank()) {
            return "";
        }
        String key = wire.trim().toUpperCase(Locale.ROOT);
        if ("OTHER".equals(key)) {
            return LoanNature.OTHER.label();
        }
        return NATURE_LABELS.getOrDefault(key, key);
    }

    public static String frequencyLabel(String wire) {
        if (wire == null || wire.isBlank()) {
            return "";
        }
        String key = wire.trim().toUpperCase(Locale.ROOT);
        return FREQUENCY_LABELS.getOrDefault(key, key);
    }

    public static String normalizeNature(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("loan nature is required");
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        Optional<LoanNature> match = Arrays.stream(LoanNature.values())
                .filter(n -> n.wire().equals(key))
                .findFirst();
        if (match.isEmpty()) {
            throw new IllegalArgumentException("Unknown loan nature: " + raw);
        }
        return match.get().wire();
    }

    public static String normalizeFrequency(String raw) {
        if (raw == null || raw.isBlank()) {
            return PaymentFrequency.MONTHLY.wire();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(PaymentFrequency.values())
                .filter(f -> f.wire().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment frequency: " + raw))
                .wire();
    }
}
