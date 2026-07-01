package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import com.svp.tracker.finance.repository.RobinhoodAccountTrackerConfigRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-owner Robinhood account suffixes and Daily Tracker exclusions (derived from Agentic sync). */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAccountTrackerConfigService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final Instant DEFAULT_RH_TRACK_START =
            ZonedDateTime.of(2026, 4, 5, 0, 0, 0, 0, CENTRAL).toInstant();
    /** Placeholder until the first successful Agentic sync fills real suffixes. */
    private static final String UNSET_SUFFIX = "0000";

    private final RobinhoodAccountTrackerConfigRepository configRepository;

    @Transactional(readOnly = true)
    public RobinhoodAccountTrackerConfig getOrCreateConfig(long ownerUserId) {
        return configRepository
                .findByOwnerUserId(ownerUserId)
                .map(this::ensureRhTrackStart)
                .orElseGet(() -> createDefaultConfig(ownerUserId));
    }

    @Transactional(readOnly = true)
    public boolean isExcludedSuffix(long ownerUserId, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return false;
        }
        String normalized = suffix.trim();
        return configRepository
                .findByOwnerUserId(ownerUserId)
                .map(cfg -> parseExcludedSuffixes(cfg.getExcludedAccountSuffixes()).contains(normalized))
                .orElse(false);
    }

    /**
     * After Agentic sync, align tracked account suffixes with Robinhood roles (default, agentic, managed) and
     * auto-exclude any other synced accounts from Daily Tracker.
     */
    @Transactional
    public void applyRolesFromSync(long ownerUserId, JsonNode syncResult) {
        if (syncResult == null || !syncResult.path("accounts").isArray()) {
            return;
        }
        RobinhoodAccountTrackerConfig config = getOrCreateConfig(ownerUserId);
        boolean changed = false;

        String defaultSuffix = null;
        String agenticSuffix = null;
        String managedSuffix = null;
        LinkedHashSet<String> allSuffixes = new LinkedHashSet<>();

        for (JsonNode row : syncResult.withArray("accounts")) {
            String accountNumber = textOrNull(row.get("account_number"));
            String suffix = suffixFromAccountNumber(accountNumber);
            if (suffix == null) {
                continue;
            }
            allSuffixes.add(suffix);
            String role = textOrNull(row.get("role"));
            if (role == null) {
                continue;
            }
            switch (role) {
                case "default" -> defaultSuffix = suffix;
                case "agentic" -> agenticSuffix = suffix;
                case "managed" -> managedSuffix = suffix;
                default -> { /* other accounts may be excluded below */ }
            }
        }

        if (agenticSuffix == null) {
            String fromTop = suffixFromAccountNumber(textOrNull(syncResult.get("agentic_account_number")));
            if (fromTop != null) {
                agenticSuffix = fromTop;
                allSuffixes.add(fromTop);
            }
        }

        if (defaultSuffix != null && !defaultSuffix.equals(config.getIndividualAccountSuffix())) {
            config.setIndividualAccountSuffix(defaultSuffix);
            changed = true;
        }
        if (agenticSuffix != null && !agenticSuffix.equals(config.getAgenticAccountSuffix())) {
            config.setAgenticAccountSuffix(agenticSuffix);
            changed = true;
        }
        if (managedSuffix != null) {
            if (!managedSuffix.equals(config.getManagedAccountSuffix())) {
                config.setManagedAccountSuffix(managedSuffix);
                changed = true;
            }
        } else if (config.getManagedAccountSuffix() != null
                && !config.getManagedAccountSuffix().isBlank()
                && !allSuffixes.contains(config.getManagedAccountSuffix().trim())) {
            config.setManagedAccountSuffix(null);
            changed = true;
        }

        Set<String> tracked = new LinkedHashSet<>();
        if (config.getIndividualAccountSuffix() != null && !isUnsetSuffix(config.getIndividualAccountSuffix())) {
            tracked.add(config.getIndividualAccountSuffix().trim());
        }
        if (config.getAgenticAccountSuffix() != null && !isUnsetSuffix(config.getAgenticAccountSuffix())) {
            tracked.add(config.getAgenticAccountSuffix().trim());
        }
        String managed = config.getManagedAccountSuffix();
        if (managed != null && !managed.isBlank()) {
            tracked.add(managed.trim());
        }

        List<String> excluded = allSuffixes.stream()
                .filter(s -> !tracked.contains(s))
                .sorted()
                .toList();
        String excludedCsv = formatExcludedSuffixes(excluded);
        if (!excludedCsv.equals(config.getExcludedAccountSuffixes())) {
            config.setExcludedAccountSuffixes(excludedCsv);
            changed = true;
        }

        if (changed) {
            config.setUpdatedAt(Instant.now());
            configRepository.save(config);
            log.info(
                    "RH account tracker config updated for user {}: individual=••••{} agentic=••••{} managed={} excluded={}",
                    ownerUserId,
                    config.getIndividualAccountSuffix(),
                    config.getAgenticAccountSuffix(),
                    config.getManagedAccountSuffix(),
                    excluded);
        }
    }

    private RobinhoodAccountTrackerConfig ensureRhTrackStart(RobinhoodAccountTrackerConfig config) {
        boolean changed = false;
        if (config.getRhAccountsTrackStartedAt() == null) {
            config.setRhAccountsTrackStartedAt(DEFAULT_RH_TRACK_START);
            changed = true;
        }
        if (config.getIndividualStartingTotalValue() == null) {
            config.setIndividualStartingTotalValue(BigDecimal.ZERO);
            changed = true;
        }
        if (config.getAgenticStartingTotalValue() == null) {
            config.setAgenticStartingTotalValue(BigDecimal.ZERO);
            changed = true;
        }
        String managed = config.getManagedAccountSuffix();
        if (managed != null && !managed.isBlank() && config.getManagedStartingTotalValue() == null) {
            config.setManagedStartingTotalValue(BigDecimal.ZERO);
            changed = true;
        }
        if (changed) {
            config.setUpdatedAt(Instant.now());
            return configRepository.save(config);
        }
        return config;
    }

    private RobinhoodAccountTrackerConfig createDefaultConfig(long ownerUserId) {
        Instant now = Instant.now();
        Instant nbisTrackingStart = ZonedDateTime.of(2026, 6, 24, 0, 0, 0, 0, CENTRAL).toInstant();

        RobinhoodAccountTrackerConfig config = new RobinhoodAccountTrackerConfig();
        config.setOwnerUserId(ownerUserId);
        config.setTrackingStartedAt(nbisTrackingStart);
        config.setRhAccountsTrackStartedAt(DEFAULT_RH_TRACK_START);
        config.setIndividualAccountSuffix(UNSET_SUFFIX);
        config.setIndividualBaselineNbis(BigDecimal.ZERO);
        config.setIndividualStartingTotalValue(BigDecimal.ZERO);
        config.setAgenticAccountSuffix(UNSET_SUFFIX);
        config.setAgenticStartingTotalValue(BigDecimal.ZERO);
        config.setExcludedAccountSuffixes("");
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        return configRepository.save(config);
    }

    static List<String> parseExcludedSuffixes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    static String formatExcludedSuffixes(List<String> suffixes) {
        if (suffixes == null || suffixes.isEmpty()) {
            return "";
        }
        return suffixes.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    static String suffixFromAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String digits = accountNumber.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return null;
        }
        return digits.substring(digits.length() - 4);
    }

    static boolean isUnsetSuffix(String suffix) {
        return suffix == null || suffix.isBlank() || UNSET_SUFFIX.equals(suffix.trim());
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.asText(null);
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
