package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.RobinhoodRhCashFlowEventDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps individual-account CSV cash flows to all RH accounts (mirrors internal transfers). */
final class RobinhoodRhCashFlowAllocator {

    private static final Pattern SUFFIX_IN_TEXT =
            Pattern.compile("(?:ENDING\\s+IN|••••|\\bACCT\\b|\\bACCOUNT\\b\\s*(?:ENDING\\s+IN)?\\s*)(\\d{4})\\b", Pattern.CASE_INSENSITIVE);

    private RobinhoodRhCashFlowAllocator() {}

    static Map<String, List<RobinhoodRhCashFlowEventDto>> allocateByAccountSuffix(
            List<RobinhoodRhCashFlowEventDto> individualCsvEvents,
            String individualSuffix,
            String agenticSuffix,
            String managedSuffix,
            Set<String> knownSuffixes) {
        Map<String, List<RobinhoodRhCashFlowEventDto>> bySuffix = new LinkedHashMap<>();
        for (String suffix : knownSuffixes) {
            bySuffix.put(suffix, new ArrayList<>());
        }

        String defaultInternalTarget = pickDefaultInternalTarget(individualSuffix, agenticSuffix, knownSuffixes);

        for (RobinhoodRhCashFlowEventDto raw : individualCsvEvents) {
            if (raw.internalTransfer()) {
                if ("OUT".equals(raw.direction())) {
                    bySuffix.computeIfAbsent(individualSuffix, k -> new ArrayList<>()).add(raw);
                    String targetSuffix =
                            resolveCounterpartySuffix(raw.description(), individualSuffix, agenticSuffix, managedSuffix, knownSuffixes)
                                    .orElse(defaultInternalTarget);
                    if (targetSuffix != null && !targetSuffix.equals(individualSuffix)) {
                        RobinhoodRhCashFlowEventDto mirror =
                                mirrorInternalIn(raw, targetSuffix, individualSuffix);
                        bySuffix.computeIfAbsent(targetSuffix, k -> new ArrayList<>()).add(mirror);
                    }
                } else if ("IN".equals(raw.direction())) {
                    bySuffix.computeIfAbsent(individualSuffix, k -> new ArrayList<>()).add(raw);
                    String sourceSuffix =
                            resolveCounterpartySuffix(raw.description(), individualSuffix, agenticSuffix, managedSuffix, knownSuffixes)
                                    .orElse(null);
                    if (sourceSuffix != null && !sourceSuffix.equals(individualSuffix)) {
                        bySuffix.computeIfAbsent(sourceSuffix, k -> new ArrayList<>()).add(mirrorInternalOut(raw, sourceSuffix, individualSuffix));
                    }
                }
            } else if ("IN".equals(raw.direction()) || "OUT".equals(raw.direction())) {
                bySuffix.computeIfAbsent(individualSuffix, k -> new ArrayList<>()).add(raw);
            }
        }

        for (List<RobinhoodRhCashFlowEventDto> events : bySuffix.values()) {
            events.sort(
                    (a, b) -> {
                        if ("STARTING_BALANCE".equals(a.flowCategory()) && !"STARTING_BALANCE".equals(b.flowCategory())) {
                            return -1;
                        }
                        if ("STARTING_BALANCE".equals(b.flowCategory()) && !"STARTING_BALANCE".equals(a.flowCategory())) {
                            return 1;
                        }
                        if (a.activityDate() == null && b.activityDate() == null) {
                            return 0;
                        }
                        if (a.activityDate() == null) {
                            return 1;
                        }
                        if (b.activityDate() == null) {
                            return -1;
                        }
                        return a.activityDate().compareTo(b.activityDate());
                    });
        }
        return bySuffix;
    }

    static RobinhoodRhCashFlowEventDto startingBalanceEvent(LocalDate asOf, BigDecimal amount) {
        return new RobinhoodRhCashFlowEventDto(
                asOf,
                "IN",
                scaleMoney(amount),
                "Starting balance",
                "Account value at tracking cutoff",
                "Config",
                "STARTING_BALANCE",
                false,
                null);
    }

    static Set<String> collectKnownSuffixes(
            Set<String> accountNumbers,
            String individualSuffix,
            String agenticSuffix,
            String managedSuffix) {
        LinkedHashSet<String> suffixes = new LinkedHashSet<>();
        suffixes.add(individualSuffix);
        suffixes.add(agenticSuffix);
        if (managedSuffix != null && !managedSuffix.isBlank()) {
            suffixes.add(managedSuffix.trim());
        }
        for (String acct : accountNumbers) {
            if (acct != null && acct.length() >= 4) {
                suffixes.add(acct.substring(acct.length() - 4));
            }
        }
        return suffixes;
    }

    private static RobinhoodRhCashFlowEventDto mirrorInternalIn(
            RobinhoodRhCashFlowEventDto outFromIndividual, String targetSuffix, String sourceSuffix) {
        String desc = outFromIndividual.description() == null
                ? "Internal transfer from ••••" + sourceSuffix
                : outFromIndividual.description() + " (mirrored to ••••" + targetSuffix + ")";
        return new RobinhoodRhCashFlowEventDto(
                outFromIndividual.activityDate(),
                "IN",
                outFromIndividual.amount(),
                outFromIndividual.transCode(),
                desc,
                "Derived",
                "INTERNAL_IN",
                true,
                maskSuffix(sourceSuffix));
    }

    private static RobinhoodRhCashFlowEventDto mirrorInternalOut(
            RobinhoodRhCashFlowEventDto inToIndividual, String sourceSuffix, String targetSuffix) {
        return new RobinhoodRhCashFlowEventDto(
                inToIndividual.activityDate(),
                "OUT",
                inToIndividual.amount(),
                inToIndividual.transCode(),
                "Internal transfer to ••••" + targetSuffix,
                "Derived",
                "INTERNAL_OUT",
                true,
                maskSuffix(targetSuffix));
    }

    private static Optional<String> resolveCounterpartySuffix(
            String description,
            String individualSuffix,
            String agenticSuffix,
            String managedSuffix,
            Set<String> knownSuffixes) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String desc = description.toUpperCase(Locale.ROOT);
        if (desc.contains("AGENTIC")) {
            return knownSuffixes.contains(agenticSuffix) ? Optional.of(agenticSuffix) : Optional.empty();
        }
        if (desc.contains("MANAGED")) {
            return managedSuffix != null && knownSuffixes.contains(managedSuffix) ? Optional.of(managedSuffix) : Optional.empty();
        }
        if (desc.contains("ROTH") || desc.contains(" IRA")) {
            for (String suffix : knownSuffixes) {
                if (!suffix.equals(individualSuffix) && !suffix.equals(agenticSuffix) && desc.contains(suffix)) {
                    return Optional.of(suffix);
                }
            }
        }
        Matcher matcher = SUFFIX_IN_TEXT.matcher(description);
        while (matcher.find()) {
            String suffix = matcher.group(1);
            if (knownSuffixes.contains(suffix)) {
                return Optional.of(suffix);
            }
        }
        return Optional.empty();
    }

    private static String pickDefaultInternalTarget(
            String individualSuffix, String agenticSuffix, Set<String> knownSuffixes) {
        if (knownSuffixes.contains(agenticSuffix) && !agenticSuffix.equals(individualSuffix)) {
            return agenticSuffix;
        }
        for (String suffix : knownSuffixes) {
            if (!suffix.equals(individualSuffix)) {
                return suffix;
            }
        }
        return null;
    }

    private static String maskSuffix(String suffix) {
        return "••••" + suffix;
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v.setScale(2, RoundingMode.HALF_UP);
    }
}
