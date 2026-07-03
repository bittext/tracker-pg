package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticBankingProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticBankingConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticBankingTransaction;
import com.svp.tracker.finance.dto.RobinhoodAgenticBankingStatusDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticBankingSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticBankingTransactionDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticBankingTransactionsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticTokensRequestDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticBankingConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticBankingTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticBankingService {

    private static final BigDecimal MICRO = BigDecimal.valueOf(1_000_000L);

    private final RobinhoodAgenticBankingProperties props;
    private final CurrentUserService currentUser;
    private final RobinhoodAgenticBankingConnectionRepository connectionRepository;
    private final RobinhoodAgenticBankingTransactionRepository transactionRepository;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticTokenCrypto tokenCrypto;
    private final RobinhoodAgenticBankingTokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public RobinhoodAgenticBankingStatusDto status() {
        long uid = currentUser.requireUserId();
        return connectionRepository
                .findByOwnerUserId(uid)
                .map(this::toStatusDto)
                .orElseGet(() -> emptyStatus());
    }

    @Transactional
    public RobinhoodAgenticBankingStatusDto saveTokens(RobinhoodAgenticTokensRequestDto request) {
        requireFeature();
        if (request.accessToken() == null || request.accessToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessToken is required");
        }
        long uid = currentUser.requireUserId();
        RobinhoodAgenticBankingConnection conn = connectionRepository
                .findByOwnerUserId(uid)
                .orElseGet(RobinhoodAgenticBankingConnection::new);
        conn.setOwnerUserId(uid);
        conn.setAccessToken(tokenCrypto.seal(request.accessToken().trim()));
        if (request.refreshToken() != null && !request.refreshToken().isBlank()) {
            conn.setRefreshToken(tokenCrypto.seal(request.refreshToken().trim()));
        }
        if (conn.getConnectedAt() == null) {
            conn.setConnectedAt(Instant.now());
        }
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);
        log.info("Robinhood Agentic Banking tokens saved for user {}", uid);
        return status();
    }

    @Transactional
    public void disconnect() {
        long uid = currentUser.requireUserId();
        transactionRepository.deleteAllByOwnerUserId(uid);
        connectionRepository.deleteByOwnerUserId(uid);
        log.info("Robinhood Agentic Banking disconnected for user {}", uid);
    }

    @Transactional
    public RobinhoodAgenticBankingSyncResultDto syncNow() {
        requireFeature();
        long uid = currentUser.requireUserId();
        RobinhoodAgenticBankingConnection conn = connectionRepository
                .findByOwnerUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Not connected — paste Banking MCP tokens first"));
        JsonNode payload;
        try {
            payload = syncFromSidecar(conn);
        } catch (IllegalStateException | RobinhoodAgenticUnauthorizedException e) {
            conn.setLastSyncAt(Instant.now());
            conn.setLastSyncStatus("error");
            conn.setLastSyncMessage(truncateMessage(e.getMessage()));
            conn.setUpdatedAt(Instant.now());
            connectionRepository.save(conn);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
        persistSyncResult(conn, payload);
        return new RobinhoodAgenticBankingSyncResultDto(
                true,
                conn.getLastSyncAt(),
                conn.getCardLastFour(),
                conn.getCardStatus(),
                conn.getActivationStatus(),
                microToUsd(conn.getMonthlyLimitMicro()),
                microToUsd(conn.getTotalSpendMicro()),
                microToUsd(conn.getAvailableBalanceMicro()),
                transactionRepository.findTop50ByOwnerUserIdOrderByTransactionAtDescIdDesc(uid).size(),
                conn.getLastSyncMessage() == null ? "" : conn.getLastSyncMessage());
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticBankingTransactionsDto transactions() {
        long uid = currentUser.requireUserId();
        List<RobinhoodAgenticBankingTransactionDto> rows = transactionRepository
                .findTop50ByOwnerUserIdOrderByTransactionAtDescIdDesc(uid)
                .stream()
                .map(this::toTransactionDto)
                .toList();
        return new RobinhoodAgenticBankingTransactionsDto(rows);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    protected JsonNode syncFromSidecar(RobinhoodAgenticBankingConnection conn) {
        return tokenService.withFreshToken(conn, token -> sidecarClient.bankingSync(token, 20));
    }

    private void persistSyncResult(RobinhoodAgenticBankingConnection conn, JsonNode payload) {
        long uid = conn.getOwnerUserId();
        Instant now = Instant.now();
        conn.setCardLastFour(textOrNull(payload, "card_last_four"));
        conn.setCardStatus(textOrNull(payload, "card_status"));
        conn.setActivationStatus(textOrNull(payload, "activation_status"));
        conn.setMonthlyLimitMicro(longOrNull(payload, "monthly_limit_micro"));
        conn.setTotalSpendMicro(longOrNull(payload, "total_spend_micro"));
        conn.setAvailableBalanceMicro(longOrNull(payload, "available_balance_micro"));
        conn.setLastSyncAt(now);
        conn.setLastSyncStatus("ok");
        conn.setLastSyncMessage("Synced Agentic Credit Card");
        conn.setUpdatedAt(now);
        try {
            conn.setSnapshotJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            conn.setSnapshotJson("");
        }
        connectionRepository.save(conn);

        transactionRepository.deleteAllByOwnerUserId(uid);
        List<RobinhoodAgenticBankingTransaction> batch = new ArrayList<>();
        JsonNode txns = payload.get("transactions");
        if (txns != null && txns.isArray()) {
            Iterator<JsonNode> it = txns.elements();
            while (it.hasNext()) {
                JsonNode row = it.next();
                String externalId = textOrNull(row, "external_id");
                if (externalId == null || externalId.isBlank()) {
                    continue;
                }
                RobinhoodAgenticBankingTransaction txn = new RobinhoodAgenticBankingTransaction();
                txn.setOwnerUserId(uid);
                txn.setExternalId(externalId);
                txn.setMerchantName(textOrNull(row, "merchant_name"));
                txn.setDescription(textOrNull(row, "description"));
                txn.setAmountMicro(longOrNull(row, "amount_micro"));
                txn.setTransactionStatus(textOrNull(row, "transaction_status"));
                txn.setTransactionAt(parseInstant(textOrNull(row, "transaction_at")));
                txn.setSyncedAt(now);
                batch.add(txn);
            }
        }
        if (!batch.isEmpty()) {
            transactionRepository.saveAll(batch);
        }
    }

    private RobinhoodAgenticBankingStatusDto toStatusDto(RobinhoodAgenticBankingConnection conn) {
        long uid = conn.getOwnerUserId();
        int txnCount = transactionRepository.findTop50ByOwnerUserIdOrderByTransactionAtDescIdDesc(uid).size();
        return new RobinhoodAgenticBankingStatusDto(
                props.enabled(),
                props.serviceConfigured(),
                true,
                conn.getCardLastFour() == null ? "" : conn.getCardLastFour(),
                conn.getCardStatus() == null ? "" : conn.getCardStatus(),
                conn.getActivationStatus() == null ? "" : conn.getActivationStatus(),
                microToUsd(conn.getMonthlyLimitMicro()),
                microToUsd(conn.getTotalSpendMicro()),
                microToUsd(conn.getAvailableBalanceMicro()),
                conn.getConnectedAt(),
                conn.getLastSyncAt(),
                conn.getLastSyncStatus() == null ? "" : conn.getLastSyncStatus(),
                conn.getLastSyncMessage() == null ? "" : conn.getLastSyncMessage(),
                txnCount);
    }

    private RobinhoodAgenticBankingStatusDto emptyStatus() {
        return new RobinhoodAgenticBankingStatusDto(
                props.enabled(),
                props.serviceConfigured(),
                false,
                "",
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                "",
                "",
                0);
    }

    private RobinhoodAgenticBankingTransactionDto toTransactionDto(RobinhoodAgenticBankingTransaction row) {
        return new RobinhoodAgenticBankingTransactionDto(
                row.getExternalId(),
                row.getMerchantName() == null ? "" : row.getMerchantName(),
                row.getDescription() == null ? "" : row.getDescription(),
                microToUsd(row.getAmountMicro()),
                row.getTransactionStatus() == null ? "" : row.getTransactionStatus(),
                row.getTransactionAt());
    }

    private void requireFeature() {
        if (!props.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Robinhood Agentic Banking is disabled on this server");
        }
        if (!props.serviceConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Robinhood Agentic Banking sidecar is not configured");
        }
    }

    private static BigDecimal microToUsd(Long micro) {
        if (micro == null) {
            return null;
        }
        return BigDecimal.valueOf(micro).divide(MICRO, 2, RoundingMode.HALF_UP);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String text = node.get(field).asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.parseLong(value.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String truncateMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
