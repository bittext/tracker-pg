package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RhIndividualMarginPeek;
import com.svp.tracker.finance.domain.RhPredictClose;
import com.svp.tracker.finance.domain.RhPredictDaily;
import com.svp.tracker.finance.domain.RhPredictSnapshot;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.dto.RhPredictCloseDto;
import com.svp.tracker.finance.dto.RhPredictCloseLabelRequest;
import com.svp.tracker.finance.dto.RhPredictDailyDto;
import com.svp.tracker.finance.dto.RhPredictDeskDto;
import com.svp.tracker.finance.repository.RhIndividualMarginPeekRepository;
import com.svp.tracker.finance.repository.RhPredictCloseRepository;
import com.svp.tracker.finance.repository.RhPredictDailyRepository;
import com.svp.tracker.finance.repository.RhPredictSnapshotRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Robinhood Predict (event contracts). Separate from community-sentiment Predicts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RhPredictService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final int DAILY_LOOKBACK_DAYS = 90;

    private final CurrentUserService currentUser;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final RhPredictSnapshotRepository snapshotRepository;
    private final RhPredictCloseRepository closeRepository;
    private final RhPredictDailyRepository dailyRepository;
    private final RhIndividualMarginPeekRepository marginPeekRepository;

    public RhPredictDeskDto loadForCurrentUser() {
        long uid = currentUser.requireUserId();
        if (snapshotRepository.findByOwnerUserId(uid).isEmpty()
                && agenticProps.serviceConfigured()
                && connectionRepository.findByOwnerUserId(uid).isPresent()) {
            return syncOwner(uid);
        }
        return loadOwner(uid);
    }

    @Transactional
    public RhPredictDeskDto refreshForCurrentUser() {
        return syncOwner(currentUser.requireUserId());
    }

    @Transactional
    public RhPredictDeskDto setOpenCost(BigDecimal amount) {
        long uid = currentUser.requireUserId();
        Instant now = Instant.now();
        RhPredictSnapshot snap = snapshotRepository.findByOwnerUserId(uid).orElseGet(RhPredictSnapshot::new);
        if (snap.getId() == null) {
            snap.setOwnerUserId(uid);
            snap.setCreatedAt(now);
            snap.setLastSyncedAt(now);
            snap.setRealizedAll(BigDecimal.ZERO);
            snap.setRealizedWeek(BigDecimal.ZERO);
            snap.setCloseCount(0);
        }
        snap.setOpenCost(amount == null ? null : amount.setScale(6, RoundingMode.HALF_UP));
        snap.setUpdatedAt(now);
        snapshotRepository.save(snap);
        return loadOwner(uid);
    }

    @Transactional
    public RhPredictCloseDto renameClose(long id, RhPredictCloseLabelRequest body) {
        long uid = currentUser.requireUserId();
        RhPredictClose row = closeRepository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String label = body == null || body.label() == null ? "" : body.label().trim();
        row.setLabel(label.isEmpty() ? null : label);
        row.setUpdatedAt(Instant.now());
        return toCloseDto(closeRepository.save(row));
    }

    /** Admin cron: refresh every connected owner. */
    public void syncAllConnections() {
        if (!agenticProps.serviceConfigured()) {
            return;
        }
        List<RobinhoodAgenticConnection> connections = connectionRepository.findAll();
        for (RobinhoodAgenticConnection conn : connections) {
            try {
                syncOwner(conn.getOwnerUserId());
            } catch (Exception e) {
                log.warn("Robinhood Predict sync failed for user {}: {}", conn.getOwnerUserId(), e.toString());
            }
        }
    }

    /**
     * Admin cron (9:10 PM Central): sync then freeze each owner's Central calendar day —
     * settled invested/payback/return plus open mark at capture.
     */
    public void captureDailySnapshots() {
        LocalDate asOf = LocalDate.now(CENTRAL);
        List<RobinhoodAgenticConnection> connections = connectionRepository.findAll();
        if (connections.isEmpty()) {
            log.debug("Predict daily snapshot: no Agentic connections");
            return;
        }
        for (RobinhoodAgenticConnection conn : connections) {
            long owner = conn.getOwnerUserId();
            try {
                if (agenticProps.serviceConfigured()) {
                    syncOwner(owner);
                }
                captureDailyForOwner(owner, asOf, true);
                backfillDerivedDays(owner, asOf.minusDays(DAILY_LOOKBACK_DAYS), asOf.minusDays(1));
                log.info("Predict daily snapshot ok for user {} on {}", owner, asOf);
            } catch (Exception e) {
                log.warn("Predict daily snapshot failed for user {}: {}", owner, e.toString());
            }
        }
    }

    /** Day strip for the Predict page — official EOD rows plus derived days from closes. */
    @Transactional
    public List<RhPredictDailyDto> listDailyForCurrentUser() {
        long uid = currentUser.requireUserId();
        LocalDate today = LocalDate.now(CENTRAL);
        LocalDate from = today.minusDays(DAILY_LOOKBACK_DAYS);
        backfillDerivedDays(uid, from, today.minusDays(1));
        upsertLiveToday(uid, today);
        return dailyRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(uid, from, today).stream()
                .map(this::toDailyDto)
                .toList();
    }

    @Transactional
    public void captureDailyForOwner(long owner, LocalDate asOf, boolean official) {
        DayTotals totals = totalsForCentralDay(owner, asOf);
        RhPredictSnapshot live = snapshotRepository.findByOwnerUserId(owner).orElse(null);
        Instant now = Instant.now();
        RhPredictDaily row = dailyRepository
                .findByOwnerUserIdAndSnapshotDate(owner, asOf)
                .orElseGet(RhPredictDaily::new);
        if (row.getId() == null) {
            row.setOwnerUserId(owner);
            row.setSnapshotDate(asOf);
            row.setCreatedAt(now);
        } else if (row.isOfficial() && !official) {
            // Keep a frozen EOD row; only refresh non-open fields from later derived passes.
            row.setCloses(totals.closes);
            row.setInvested(totals.invested);
            row.setPayback(totals.payback);
            row.setReturned(totals.returned);
            row.setUpdatedAt(now);
            dailyRepository.save(row);
            return;
        }
        row.setCapturedAt(now);
        row.setCloses(totals.closes);
        row.setInvested(totals.invested);
        row.setPayback(totals.payback);
        row.setReturned(totals.returned);
        if (official) {
            row.setOpenValue(live == null ? null : live.getOpenValue());
            row.setOpenCost(live == null ? null : live.getOpenCost());
            row.setOfficial(true);
        } else if (!row.isOfficial()) {
            row.setOpenValue(null);
            row.setOpenCost(null);
            row.setOfficial(false);
        }
        row.setUpdatedAt(now);
        dailyRepository.save(row);
    }

    private void upsertLiveToday(long owner, LocalDate today) {
        DayTotals totals = totalsForCentralDay(owner, today);
        RhPredictSnapshot live = snapshotRepository.findByOwnerUserId(owner).orElse(null);
        Instant now = Instant.now();
        RhPredictDaily row = dailyRepository
                .findByOwnerUserIdAndSnapshotDate(owner, today)
                .orElseGet(RhPredictDaily::new);
        if (row.getId() == null) {
            row.setOwnerUserId(owner);
            row.setSnapshotDate(today);
            row.setCreatedAt(now);
            row.setOfficial(false);
        }
        if (row.isOfficial()) {
            // After EOD, still refresh settled totals if more closes land late.
            row.setCloses(totals.closes);
            row.setInvested(totals.invested);
            row.setPayback(totals.payback);
            row.setReturned(totals.returned);
            row.setUpdatedAt(now);
            dailyRepository.save(row);
            return;
        }
        row.setCapturedAt(now);
        row.setCloses(totals.closes);
        row.setInvested(totals.invested);
        row.setPayback(totals.payback);
        row.setReturned(totals.returned);
        row.setOpenValue(live == null ? null : live.getOpenValue());
        row.setOpenCost(live == null ? null : live.getOpenCost());
        row.setOfficial(false);
        row.setUpdatedAt(now);
        dailyRepository.save(row);
    }

    private void backfillDerivedDays(long owner, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return;
        }
        Map<LocalDate, DayTotals> byDay = new TreeMap<>();
        for (RhPredictClose row : closeRepository.findByOwnerUserIdOrderByClosedAtDescIdDesc(owner)) {
            if (row.getClosedAt() == null) {
                continue;
            }
            LocalDate day = row.getClosedAt().atZone(CENTRAL).toLocalDate();
            if (day.isBefore(from) || day.isAfter(to)) {
                continue;
            }
            byDay.merge(day, dayTotalsFromClose(row), DayTotals::plus);
        }
        Instant now = Instant.now();
        for (Map.Entry<LocalDate, DayTotals> entry : byDay.entrySet()) {
            RhPredictDaily existing = dailyRepository
                    .findByOwnerUserIdAndSnapshotDate(owner, entry.getKey())
                    .orElse(null);
            if (existing != null && existing.isOfficial()) {
                existing.setCloses(entry.getValue().closes);
                existing.setInvested(entry.getValue().invested);
                existing.setPayback(entry.getValue().payback);
                existing.setReturned(entry.getValue().returned);
                existing.setUpdatedAt(now);
                dailyRepository.save(existing);
                continue;
            }
            DayTotals totals = entry.getValue();
            RhPredictDaily row = existing != null ? existing : new RhPredictDaily();
            if (row.getId() == null) {
                row.setOwnerUserId(owner);
                row.setSnapshotDate(entry.getKey());
                row.setCreatedAt(now);
                row.setOfficial(false);
                row.setOpenValue(null);
                row.setOpenCost(null);
            }
            row.setCapturedAt(now);
            row.setCloses(totals.closes);
            row.setInvested(totals.invested);
            row.setPayback(totals.payback);
            row.setReturned(totals.returned);
            row.setUpdatedAt(now);
            dailyRepository.save(row);
        }
    }

    private DayTotals totalsForCentralDay(long owner, LocalDate asOf) {
        DayTotals totals = DayTotals.EMPTY;
        for (RhPredictClose row : closeRepository.findByOwnerUserIdOrderByClosedAtDescIdDesc(owner)) {
            if (row.getClosedAt() == null) {
                continue;
            }
            LocalDate day = row.getClosedAt().atZone(CENTRAL).toLocalDate();
            if (!day.equals(asOf)) {
                continue;
            }
            totals = totals.plus(dayTotalsFromClose(row));
        }
        return totals;
    }

    private static DayTotals dayTotalsFromClose(RhPredictClose row) {
        BigDecimal payback = nz(row.getQuantity()).multiply(nz(row.getPrice()));
        BigDecimal realized = nz(row.getRealized());
        BigDecimal invested = payback.subtract(realized);
        return new DayTotals(1, invested, payback, realized);
    }

    private RhPredictDailyDto toDailyDto(RhPredictDaily row) {
        return new RhPredictDailyDto(
                row.getSnapshotDate(),
                row.getCapturedAt(),
                row.getCloses(),
                nz(row.getInvested()),
                nz(row.getPayback()),
                nz(row.getReturned()),
                row.getOpenValue(),
                row.getOpenCost(),
                row.isOfficial());
    }

    private record DayTotals(int closes, BigDecimal invested, BigDecimal payback, BigDecimal returned) {
        static final DayTotals EMPTY = new DayTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        DayTotals plus(DayTotals other) {
            return new DayTotals(
                    closes + other.closes,
                    invested.add(other.invested).setScale(6, RoundingMode.HALF_UP),
                    payback.add(other.payback).setScale(6, RoundingMode.HALF_UP),
                    returned.add(other.returned).setScale(6, RoundingMode.HALF_UP));
        }
    }

    @Transactional
    public RhPredictDeskDto syncOwner(long owner) {
        if (!agenticProps.serviceConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Robinhood connection is not configured");
        }
        RobinhoodAgenticConnection conn = connectionRepository
                .findByOwnerUserId(owner)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Connect Robinhood Agentic before monitoring Predict"));
        JsonNode root = tokenService.fetchPredictMarkets(conn);
        persist(owner, root);
        return loadOwner(owner);
    }

    @Transactional
    public void persist(long owner, JsonNode root) {
        Instant now = Instant.now();
        List<String> warnings = new ArrayList<>();
        JsonNode warnNode = root == null ? null : root.get("warnings");
        if (warnNode != null && warnNode.isArray()) {
            for (JsonNode w : warnNode) {
                if (w != null && w.isTextual() && !w.asText().isBlank()) {
                    warnings.add(w.asText());
                }
            }
        }
        JsonNode accounts = root == null ? null : root.get("accounts");
        if (accounts != null && accounts.isArray()) {
            for (JsonNode account : accounts) {
                if (account == null || !account.isObject()) {
                    continue;
                }
                String suffix = text(account, "suffix");
                String label = text(account, "label");
                if (suffix == null || suffix.isBlank()) {
                    continue;
                }
                if (label == null || label.isBlank()) {
                    label = suffix;
                }
                label = truncate(label, 64);
                JsonNode closes = account.get("closes");
                if (closes == null || !closes.isArray()) {
                    continue;
                }
                for (JsonNode close : closes) {
                    upsertClose(owner, suffix, label, close, now);
                }
            }
        }
        List<RhPredictClose> stored = closeRepository.findByOwnerUserIdOrderByClosedAtDescIdDesc(owner);
        List<RhPredictClose> repeated = cumulativeSnapshots(stored);
        if (!repeated.isEmpty()) {
            closeRepository.deleteAll(repeated);
            stored.removeAll(repeated);
        }
        BigDecimal realizedAll = BigDecimal.ZERO;
        BigDecimal realizedWeek = BigDecimal.ZERO;
        Instant weekStart = now.minus(7, ChronoUnit.DAYS);
        for (RhPredictClose row : stored) {
            realizedAll = realizedAll.add(nz(row.getRealized()));
            if (row.getClosedAt() != null && !row.getClosedAt().isBefore(weekStart)) {
                realizedWeek = realizedWeek.add(nz(row.getRealized()));
            }
        }
        RhPredictSnapshot snap = snapshotRepository.findByOwnerUserId(owner).orElseGet(RhPredictSnapshot::new);
        if (snap.getId() == null) {
            snap.setOwnerUserId(owner);
            snap.setCreatedAt(now);
        }
        snap.setLastSyncedAt(now);
        BigDecimal openValue = decimal(root, "open_value");
        snap.setOpenValue(openValue);
        // A missing cost means Robinhood did not name one on this pull. Keeping the previous
        // figure makes a settled book look like it is still invested (mark $48, cost $1,300).
        if (openValue == null || openValue.signum() <= 0) {
            snap.setOpenCost(null);
        } else {
            snap.setOpenCost(decimal(root, "open_cost"));
        }
        snap.setRealizedAll(realizedAll.setScale(6, RoundingMode.HALF_UP));
        snap.setRealizedWeek(realizedWeek.setScale(6, RoundingMode.HALF_UP));
        snap.setCloseCount(stored.size());
        snap.setWarnings(warnings.isEmpty() ? null : String.join("\n", warnings));
        snap.setUpdatedAt(now);
        snapshotRepository.save(snap);
    }

    private void upsertClose(long owner, String suffix, String accountLabel, JsonNode close, Instant now) {
        Instant closedAt = instant(text(close, "timestamp"));
        if (closedAt == null) {
            return;
        }
        BigDecimal quantity = nz(decimal(close, "quantity"));
        BigDecimal price = nz(decimal(close, "price"));
        BigDecimal realized = nz(decimal(close, "realized"));
        String fingerprint = suffix + "|" + closedAt + "|" + quantity.toPlainString() + "|" + price.toPlainString()
                + "|" + realized.toPlainString();
        RhPredictClose row = closeRepository
                .findByOwnerUserIdAndFingerprint(owner, fingerprint)
                .orElseGet(RhPredictClose::new);
        if (row.getId() == null) {
            row.setOwnerUserId(owner);
            row.setFingerprint(fingerprint);
            row.setCreatedAt(now);
        }
        row.setAccountSuffix(suffix);
        row.setAccountLabel(accountLabel);
        row.setClosedAt(closedAt);
        row.setQuantity(quantity);
        row.setPrice(price);
        row.setRealized(realized);
        String history = text(close, "label");
        if (history != null && !history.isBlank() && (row.getLabel() == null || row.getLabel().isBlank())) {
            row.setLabel(truncate(history, 240));
        }
        row.setUpdatedAt(now);
        closeRepository.save(row);
    }

    /**
     * Robinhood's history feed can repeat one sale as the filled size grows.
     * A later row replaces an earlier one at the same price, with a larger quantity
     * and about the same gain per contract, inside two minutes.
     */
    static List<RhPredictClose> cumulativeSnapshots(List<RhPredictClose> stored) {
        List<RhPredictClose> ordered = stored.stream()
                .filter(row -> row.getClosedAt() != null && row.getQuantity() != null && row.getPrice() != null)
                .sorted(Comparator.comparing(RhPredictClose::getClosedAt).thenComparing(RhPredictClose::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        List<RhPredictClose> drop = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            RhPredictClose earlier = ordered.get(i);
            Instant limit = earlier.getClosedAt().plus(2, ChronoUnit.MINUTES);
            for (int j = i + 1; j < ordered.size(); j++) {
                RhPredictClose later = ordered.get(j);
                if (later.getClosedAt().isAfter(limit)) {
                    break;
                }
                if (supersedes(later, earlier)) {
                    drop.add(earlier);
                    break;
                }
            }
        }
        return drop;
    }

    private static boolean supersedes(RhPredictClose later, RhPredictClose earlier) {
        if (later.getQuantity().compareTo(earlier.getQuantity()) <= 0) {
            return false;
        }
        if (earlier.getQuantity().signum() == 0 || later.getQuantity().signum() == 0) {
            return false;
        }
        if (later.getPrice().subtract(earlier.getPrice()).abs().compareTo(new BigDecimal("0.0001")) > 0) {
            return false;
        }
        if (earlier.getRealized() != null
                && later.getRealized() != null
                && earlier.getRealized().signum() != 0
                && later.getRealized().signum() != 0
                && earlier.getRealized().signum() != later.getRealized().signum()) {
            return false;
        }
        BigDecimal unitEarlier = nz(earlier.getRealized()).divide(earlier.getQuantity(), 6, RoundingMode.HALF_UP);
        BigDecimal unitLater = nz(later.getRealized()).divide(later.getQuantity(), 6, RoundingMode.HALF_UP);
        return unitLater.subtract(unitEarlier).abs().compareTo(new BigDecimal("0.02")) <= 0;
    }

    private RhPredictDeskDto loadOwner(long owner) {
        RhPredictSnapshot snap = snapshotRepository.findByOwnerUserId(owner).orElse(null);
        List<RhPredictCloseDto> closes = closeRepository.findByOwnerUserIdOrderByClosedAtDescIdDesc(owner).stream()
                .map(this::toCloseDto)
                .toList();
        RhIndividualMarginPeek peek = marginPeekRepository
                .findTopByOwnerUserIdAndAccountSuffixOrderByCapturedAtDesc(
                        owner, RobinhoodIndividualMarginMath.ACCOUNT_SUFFIX)
                .orElse(null);
        BigDecimal buyingPower = peek == null || peek.getBuyingPower() == null
                ? null
                : peek.getBuyingPower().setScale(2, RoundingMode.HALF_UP);
        Instant buyingPowerAsOf = peek == null ? null : peek.getCapturedAt();
        if (snap == null) {
            return new RhPredictDeskDto(
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    closes.size(),
                    List.of(),
                    closes,
                    buyingPower,
                    buyingPowerAsOf);
        }
        List<String> warnings = new ArrayList<>();
        if (snap.getWarnings() != null && !snap.getWarnings().isBlank()) {
            warnings.addAll(List.of(snap.getWarnings().split("\\R")));
        }
        return new RhPredictDeskDto(
                snap.getLastSyncedAt(),
                snap.getOpenValue(),
                snap.getOpenCost(),
                nz(snap.getRealizedAll()),
                nz(snap.getRealizedWeek()),
                snap.getCloseCount(),
                warnings,
                closes,
                buyingPower,
                buyingPowerAsOf);
    }

    private RhPredictCloseDto toCloseDto(RhPredictClose row) {
        return new RhPredictCloseDto(
                row.getId(),
                row.getAccountSuffix(),
                row.getAccountLabel(),
                row.getClosedAt(),
                row.getQuantity(),
                row.getPrice(),
                row.getRealized(),
                row.getLabel());
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(t).toInstant();
            } catch (Exception ignored2) {
                return null;
            }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v != null && v.isTextual() && !v.asText().isBlank()) {
            return v.asText().trim();
        }
        if (v != null && v.isNumber()) {
            return v.asText();
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual()) {
            String t = v.asText().trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
