package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.FinanceInsuranceOptionDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FinanceInsuranceCatalog {

    public enum PolicyType {
        AUTO("Auto"),
        HOME("Homeowners"),
        RENTERS("Renters"),
        HEALTH("Health"),
        LIFE("Life"),
        DISABILITY("Disability"),
        UMBRELLA("Umbrella"),
        PET("Pet"),
        TRAVEL("Travel"),
        BUSINESS("Business"),
        OTHER("Other");

        private final String label;

        PolicyType(String label) {
            this.label = label;
        }

        public String wire() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    public enum PremiumFrequency {
        WEEKLY("Weekly"),
        BIWEEKLY("Bi-weekly"),
        MONTHLY("Monthly"),
        QUARTERLY("Quarterly"),
        SEMI_ANNUAL("Semi-annual"),
        ANNUAL("Annual"),
        ONE_TIME("One-time"),
        OTHER("Other");

        private final String label;

        PremiumFrequency(String label) {
            this.label = label;
        }

        public String wire() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    private static final Map<String, String> TYPE_LABELS =
            Arrays.stream(PolicyType.values()).collect(Collectors.toMap(PolicyType::wire, PolicyType::label));

    private static final Map<String, String> FREQUENCY_LABELS = Arrays.stream(PremiumFrequency.values())
            .collect(Collectors.toMap(PremiumFrequency::wire, PremiumFrequency::label));

    private FinanceInsuranceCatalog() {}

    public static List<FinanceInsuranceOptionDto> policyTypeOptions() {
        return Arrays.stream(PolicyType.values())
                .map(t -> new FinanceInsuranceOptionDto(t.wire(), t.label()))
                .toList();
    }

    public static List<FinanceInsuranceOptionDto> premiumFrequencyOptions() {
        return Arrays.stream(PremiumFrequency.values())
                .map(f -> new FinanceInsuranceOptionDto(f.wire(), f.label()))
                .toList();
    }

    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Policy type is required");
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return PolicyType.valueOf(key).wire();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown policy type: " + raw);
        }
    }

    public static String normalizeFrequency(String raw) {
        if (raw == null || raw.isBlank()) {
            return PremiumFrequency.ANNUAL.wire();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return PremiumFrequency.valueOf(key).wire();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown premium frequency: " + raw);
        }
    }

    public static String typeLabel(String wire) {
        return TYPE_LABELS.getOrDefault(wire, wire);
    }

    public static String frequencyLabel(String wire) {
        return FREQUENCY_LABELS.getOrDefault(wire, wire);
    }

    public static BigDecimal annualizedPremium(BigDecimal amount, String frequencyWire) {
        if (amount == null) {
            return null;
        }
        BigDecimal annual =
                switch (Optional.ofNullable(frequencyWire).orElse(PremiumFrequency.ANNUAL.wire())) {
                    case "WEEKLY" -> amount.multiply(BigDecimal.valueOf(52));
                    case "BIWEEKLY" -> amount.multiply(BigDecimal.valueOf(26));
                    case "MONTHLY" -> amount.multiply(BigDecimal.valueOf(12));
                    case "QUARTERLY" -> amount.multiply(BigDecimal.valueOf(4));
                    case "SEMI_ANNUAL" -> amount.multiply(BigDecimal.valueOf(2));
                    case "ANNUAL" -> amount;
                    default -> null;
                };
        return annual == null ? null : annual.setScale(2, RoundingMode.HALF_UP);
    }
}
