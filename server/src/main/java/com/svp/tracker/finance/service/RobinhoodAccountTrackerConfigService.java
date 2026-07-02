package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodRhOwnedAccountsDto;
import com.svp.tracker.finance.repository.RobinhoodAccountTrackerConfigRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    /**
     * spulickal keeps role-based Daily Tracker accounts with per-user exclusions (••••0440, ••••2835).
     * Every other user gets all account suffixes from their own Agentic sync (e.g. nisha ••••4190 + ••••7581).
     */
    public static final String FULL_DAILY_TRACKER_OWNER_USERNAME = "spulickal";

    private final RobinhoodAccountTrackerConfigRepository configRepository;
    private final RobinhoodAgenticPositionRepository positionRepository;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public RobinhoodAccountTrackerConfig getOrCreateConfig(long ownerUserId) {
        return configRepository
                .findByOwnerUserId(ownerUserId)
                .map(this::ensureRhTrackStart)
                .orElseGet(() -> createDefaultConfig(ownerUserId));
    }

    @Transactional(readOnly = true)
    public RobinhoodRhOwnedAccountsDto resolveOwnedAccounts(long ownerUserId) {
        List<RobinhoodAgenticPosition> positions = positionRepository.findByOwnerUserIdOrderBySymbolAsc(ownerUserId);
        Optional<RobinhoodAgenticConnection> connectionOpt = connectionRepository.findByOwnerUserId(ownerUserId);
        LinkedHashSet<String> owned = collectOwnedSuffixes(positions, connectionOpt);
        RobinhoodAccountTrackerConfig config = getOrCreateConfig(ownerUserId);

        String individual = pickOwnedSuffix(config.getIndividualAccountSuffix(), owned);
        String agentic = pickOwnedSuffix(config.getAgenticAccountSuffix(), owned);
        if (agentic == null) {
            agentic = connectionOpt
                    .map(RobinhoodAgenticConnection::getAgenticAccountNumber)
                    .map(RobinhoodAccountTrackerConfigService::suffixFromAccountNumber)
                    .filter(owned::contains)
                    .orElse(null);
        }
        String managed = pickOwnedSuffix(config.getManagedAccountSuffix(), owned);

        LinkedHashSet<String> tracked = new LinkedHashSet<>();
        if (individual != null) {
            tracked.add(individual);
        }
        if (agentic != null) {
            tracked.add(agentic);
        }
        if (managed != null) {
            tracked.add(managed);
        }
        List<String> excluded = parseExcludedSuffixes(config.getExcludedAccountSuffixes());
        for (String suffix : owned) {
            if (!excluded.contains(suffix)) {
                tracked.add(suffix);
            }
        }
        return new RobinhoodRhOwnedAccountsDto(
                individual,
                agentic,
                managed,
                Set.copyOf(owned),
                Set.copyOf(tracked));
    }

    @Transactional
    public void reconcileConfigWithOwnedAccounts(long ownerUserId) {
        RobinhoodRhOwnedAccountsDto owned = resolveOwnedAccounts(ownerUserId);
        if (owned.ownedSuffixes().isEmpty()) {
            return;
        }
        RobinhoodAccountTrackerConfig config = getOrCreateConfig(ownerUserId);
        boolean changed = false;
        String individual = owned.individualSuffix() != null ? owned.individualSuffix() : UNSET_SUFFIX;
        if (!individual.equals(config.getIndividualAccountSuffix())) {
            config.setIndividualAccountSuffix(individual);
            changed = true;
        }
        String agentic = owned.agenticSuffix() != null ? owned.agenticSuffix() : UNSET_SUFFIX;
        if (!agentic.equals(config.getAgenticAccountSuffix())) {
            config.setAgenticAccountSuffix(agentic);
            changed = true;
        }
        String managed = owned.managedSuffix();
        String configManaged = config.getManagedAccountSuffix();
        if (managed == null) {
            if (configManaged != null && !configManaged.isBlank()) {
                config.setManagedAccountSuffix(null);
                changed = true;
            }
        } else if (!managed.equals(configManaged)) {
            config.setManagedAccountSuffix(managed);
            changed = true;
        }
        List<String> excluded;
        if (isFullDailyTrackerOwner(ownerUserId)) {
            excluded = owned.ownedSuffixes().stream()
                    .filter(s -> !owned.trackedSuffixes().contains(s))
                    .sorted()
                    .toList();
        } else {
            excluded = List.of();
        }
        String excludedCsv = formatExcludedSuffixes(excluded);
        if (!excludedCsv.equals(config.getExcludedAccountSuffixes())) {
            config.setExcludedAccountSuffixes(excludedCsv);
            changed = true;
        }
        if (changed) {
            config.setUpdatedAt(Instant.now());
            configRepository.save(config);
            log.info(
                    "RH account tracker config reconciled for user {}: individual=••••{} agentic=••••{} managed={} owned={}",
                    ownerUserId,
                    config.getIndividualAccountSuffix(),
                    config.getAgenticAccountSuffix(),
                    config.getManagedAccountSuffix(),
                    owned.ownedSuffixes());
        }
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

    @Transactional(readOnly = true)
    public boolean isTrackedSuffix(long ownerUserId, String suffix) {
        if (suffix == null || suffix.isBlank() || isUnsetSuffix(suffix)) {
            return false;
        }
        return resolveOwnedAccounts(ownerUserId).trackedSuffixes().contains(suffix.trim());
    }

    /**
     * Whether a suffix appears in Daily Tracker for this owner. Never includes another user's accounts.
     * Non-spulickal owners: every suffix returned by their Agentic sync (all nisha-agentic accounts).
     */
    @Transactional(readOnly = true)
    public boolean isDailyTrackerSuffix(long ownerUserId, String suffix) {
        if (suffix == null || suffix.isBlank() || isUnsetSuffix(suffix)) {
            return false;
        }
        RobinhoodRhOwnedAccountsDto owned = resolveOwnedAccounts(ownerUserId);
        return matchesDailyTrackerSuffixPolicy(
                isFullDailyTrackerOwner(ownerUserId),
                owned.ownedSuffixes(),
                owned.trackedSuffixes(),
                suffix);
    }

    static boolean matchesDailyTrackerSuffixPolicy(
            boolean fullDailyTrackerOwner,
            Set<String> ownedSuffixes,
            Set<String> trackedSuffixes,
            String suffix) {
        String normalized = suffix.trim();
        if (!ownedSuffixes.contains(normalized)) {
            return false;
        }
        if (fullDailyTrackerOwner) {
            return trackedSuffixes.contains(normalized);
        }
        return true;
    }

    private boolean isFullDailyTrackerOwner(long ownerUserId) {
        return appUserRepository
                .findById(ownerUserId)
                .map(u -> FULL_DAILY_TRACKER_OWNER_USERNAME.equalsIgnoreCase(u.getUsername().trim()))
                .orElse(false);
    }

    /**
     * After Agentic sync, align tracked account suffixes with Robinhood roles (default, agentic, managed).
     * spulickal: auto-exclude other synced accounts (e.g. ••••0440). Other users: keep all synced accounts.
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
        String excludedCsv;
        if (isFullDailyTrackerOwner(ownerUserId)) {
            excludedCsv = formatExcludedSuffixes(excluded);
        } else {
            // Keep every account from this user's Agentic connection in Daily Tracker.
            excludedCsv = "";
        }
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

    private LinkedHashSet<String> collectOwnedSuffixes(
            List<RobinhoodAgenticPosition> positions, Optional<RobinhoodAgenticConnection> connectionOpt) {
        LinkedHashSet<String> owned = new LinkedHashSet<>();
        for (RobinhoodAgenticPosition position : positions) {
            String suffix = suffixFromAccountNumber(position.getAccountNumber());
            if (suffix != null) {
                owned.add(suffix);
            }
        }
        connectionOpt.ifPresent(conn -> {
            for (String accountNumber : parsePortfolioAccountNumbers(conn.getPortfolioJson())) {
                String suffix = suffixFromAccountNumber(accountNumber);
                if (suffix != null) {
                    owned.add(suffix);
                }
            }
            String agentic = suffixFromAccountNumber(conn.getAgenticAccountNumber());
            if (agentic != null) {
                owned.add(agentic);
            }
        });
        return owned;
    }

    private List<String> parsePortfolioAccountNumbers(String portfolioJson) {
        if (portfolioJson == null || portfolioJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(portfolioJson);
            if (!root.isObject()) {
                return List.of();
            }
            List<String> accounts = new ArrayList<>();
            root.fieldNames().forEachRemaining(field -> {
                if (field != null && !field.isBlank()) {
                    accounts.add(field.trim());
                }
            });
            return accounts;
        } catch (Exception e) {
            log.warn("Could not parse portfolio_json for owned-account resolution: {}", e.getMessage());
            return List.of();
        }
    }

    private static String pickOwnedSuffix(String configSuffix, Set<String> owned) {
        if (configSuffix == null || isUnsetSuffix(configSuffix)) {
            return null;
        }
        String normalized = configSuffix.trim();
        return owned.contains(normalized) ? normalized : null;
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
