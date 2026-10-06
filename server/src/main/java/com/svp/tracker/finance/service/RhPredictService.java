package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RhPredictClose;
import com.svp.tracker.finance.domain.RhPredictSnapshot;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.dto.RhPredictCloseDto;
import com.svp.tracker.finance.dto.RhPredictCloseLabelRequest;
import com.svp.tracker.finance.dto.RhPredictDeskDto;
import com.svp.tracker.finance.repository.RhPredictCloseRepository;
import com.svp.tracker.finance.repository.RhPredictSnapshotRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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

    private final CurrentUserService currentUser;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final RhPredictSnapshotRepository snapshotRepository;
    private final RhPredictCloseRepository closeRepository;

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
        snap.setOpenValue(decimal(root, "open_value"));
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
        row.setUpdatedAt(now);
        closeRepository.save(row);
    }

    private RhPredictDeskDto loadOwner(long owner) {
        RhPredictSnapshot snap = snapshotRepository.findByOwnerUserId(owner).orElse(null);
        List<RhPredictCloseDto> closes = closeRepository.findByOwnerUserIdOrderByClosedAtDescIdDesc(owner).stream()
                .map(this::toCloseDto)
                .toList();
        if (snap == null) {
            return new RhPredictDeskDto(
                    null, null, BigDecimal.ZERO, BigDecimal.ZERO, closes.size(), List.of(), closes);
        }
        List<String> warnings = new ArrayList<>();
        if (snap.getWarnings() != null && !snap.getWarnings().isBlank()) {
            warnings.addAll(List.of(snap.getWarnings().split("\\R")));
        }
        return new RhPredictDeskDto(
                snap.getLastSyncedAt(),
                snap.getOpenValue(),
                nz(snap.getRealizedAll()),
                nz(snap.getRealizedWeek()),
                snap.getCloseCount(),
                warnings,
                closes);
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
