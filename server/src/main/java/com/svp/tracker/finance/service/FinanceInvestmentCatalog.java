package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.FinanceInvestmentOptionDto;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FinanceInvestmentCatalog {

    public enum InvestmentType {
        STOCK("Stock"),
        ETF("ETF"),
        MUTUAL_FUND("Mutual fund"),
        BOND("Bond"),
        OPTION("Option"),
        CRYPTO("Cryptocurrency"),
        REAL_ESTATE("Real estate"),
        RETIREMENT_401K("401(k)"),
        RETIREMENT_IRA("IRA / Roth IRA"),
        HSA("HSA"),
        CASH("Cash / money market"),
        PRIVATE_EQUITY("Private equity"),
        COMMODITY("Commodity"),
        OTHER("Other");

        private final String label;

        InvestmentType(String label) {
            this.label = label;
        }

        public String wire() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    private static final Map<String, String> TYPE_LABELS = Arrays.stream(InvestmentType.values())
            .collect(Collectors.toMap(InvestmentType::wire, InvestmentType::label));

    private FinanceInvestmentCatalog() {}

    public static List<FinanceInvestmentOptionDto> investmentTypeOptions() {
        return Arrays.stream(InvestmentType.values())
                .map(t -> new FinanceInvestmentOptionDto(t.wire(), t.label()))
                .toList();
    }

    public static String typeLabel(String wire) {
        if (wire == null || wire.isBlank()) {
            return "";
        }
        return TYPE_LABELS.getOrDefault(wire.trim().toUpperCase(Locale.ROOT), wire);
    }

    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("investment type is required");
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        Optional<InvestmentType> match = Arrays.stream(InvestmentType.values())
                .filter(t -> t.wire().equals(key))
                .findFirst();
        if (match.isEmpty()) {
            throw new IllegalArgumentException("Unknown investment type: " + raw);
        }
        return match.get().wire();
    }
}
