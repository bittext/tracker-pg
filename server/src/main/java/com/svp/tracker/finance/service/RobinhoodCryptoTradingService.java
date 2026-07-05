package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodCryptoTradingConnection;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingCredentialsRequestDto;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingStatusDto;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodCryptoTradingConnectionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodCryptoTradingService {

    private final RobinhoodAgenticProperties agenticProps;
    private final CurrentUserService currentUser;
    private final RobinhoodCryptoTradingConnectionRepository connectionRepository;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticTokenCrypto tokenCrypto;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public Optional<RobinhoodCryptoTradingSyncResultDto> cachedSyncResult(long ownerUserId) {
        return connectionRepository.findByOwnerUserId(ownerUserId).map(conn -> {
            List<RobinhoodRhCryptoHoldingDto> holdings = parseHoldingsFromJson(conn.getHoldingsJson());
            BigDecimal total = holdings.stream()
                    .map(h -> nullToZero(h.marketValue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new RobinhoodCryptoTradingSyncResultDto(
                    !holdings.isEmpty(),
                    conn.getLastSyncMessage() == null ? "Cached holdings" : conn.getLastSyncMessage(),
                    conn.getAccountNumber() == null ? "" : conn.getAccountNumber(),
                    scaleMoney(total),
                    holdings,
                    List.of());
        });
    }

    private List<RobinhoodRhCryptoHoldingDto> parseHoldingsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public RobinhoodCryptoTradingStatusDto status() {
        long uid = currentUser.requireUserId();
        return connectionRepository
                .findByOwnerUserId(uid)
                .map(this::toStatusDto)
                .orElseGet(this::emptyStatus);
    }

    @Transactional(readOnly = true)
    public RobinhoodCryptoTradingStatusDto statusForOwner(long ownerUserId) {
        return connectionRepository
                .findByOwnerUserId(ownerUserId)
                .map(this::toStatusDto)
                .orElseGet(this::emptyStatus);
    }

    @Transactional(readOnly = true)
    public boolean isConnected(long ownerUserId) {
        return connectionRepository.findByOwnerUserId(ownerUserId).isPresent();
    }

    @Transactional
    public RobinhoodCryptoTradingStatusDto saveCredentials(RobinhoodCryptoTradingCredentialsRequestDto request) {
        requireSidecar();
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey is required");
        }
        if (request.privateKeyBase64() == null || request.privateKeyBase64().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "privateKeyBase64 is required");
        }
        long uid = currentUser.requireUserId();
        RobinhoodCryptoTradingConnection conn = connectionRepository
                .findByOwnerUserId(uid)
                .orElseGet(RobinhoodCryptoTradingConnection::new);
        conn.setOwnerUserId(uid);
        conn.setApiKeyEnc(tokenCrypto.seal(request.apiKey().trim()));
        conn.setPrivateKeyEnc(tokenCrypto.seal(request.privateKeyBase64().trim()));
        if (conn.getConnectedAt() == null) {
            conn.setConnectedAt(Instant.now());
        }
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);
        log.info("Robinhood Crypto Trading credentials saved for user {}", uid);
        return status();
    }

    @Transactional
    public void disconnect() {
        long uid = currentUser.requireUserId();
        connectionRepository.deleteByOwnerUserId(uid);
        log.info("Robinhood Crypto Trading disconnected for user {}", uid);
    }

    @Transactional
    public RobinhoodCryptoTradingSyncResultDto syncNow() {
        requireSidecar();
        long uid = currentUser.requireUserId();
        RobinhoodCryptoTradingConnection conn = connectionRepository
                .findByOwnerUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Not connected — add Crypto Trading API credentials first"));
        return syncConnection(conn);
    }

    @Transactional
    public RobinhoodCryptoTradingSyncResultDto syncForOwner(long ownerUserId) {
        requireSidecar();
        RobinhoodCryptoTradingConnection conn = connectionRepository
                .findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Crypto Trading API not connected for user " + ownerUserId));
        return syncConnection(conn);
    }

    JsonNode syncPayloadForOwner(long ownerUserId) {
        RobinhoodCryptoTradingConnection conn = connectionRepository
                .findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Crypto Trading API not connected"));
        return callSidecar(conn);
    }

    private RobinhoodCryptoTradingSyncResultDto syncConnection(RobinhoodCryptoTradingConnection conn) {
        JsonNode payload;
        try {
            payload = callSidecar(conn);
        } catch (IllegalStateException e) {
            markSyncError(conn, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
        return persistSyncResult(conn, payload);
    }

    private JsonNode callSidecar(RobinhoodCryptoTradingConnection conn) {
        String apiKey = tokenCrypto.open(conn.getApiKeyEnc());
        String privateKey = tokenCrypto.open(conn.getPrivateKeyEnc());
        return sidecarClient.cryptoSync(apiKey, privateKey);
    }

    RobinhoodCryptoTradingSyncResultDto persistSyncResult(
            RobinhoodCryptoTradingConnection conn, JsonNode payload) {
        Instant now = Instant.now();
        boolean ok = payload.path("ok").asBoolean(false);
        String message = textOrNull(payload, "message");
        if (message == null) {
            message = ok ? "Crypto sync ok" : "Crypto sync failed";
        }
        conn.setAccountNumber(textOrNull(payload, "account_number"));
        conn.setLastSyncAt(now);
        conn.setLastSyncStatus(ok ? "ok" : "error");
        conn.setLastSyncMessage(truncate(message));
        conn.setUpdatedAt(now);
        try {
            conn.setHoldingsJson(objectMapper.writeValueAsString(payload.path("holdings")));
        } catch (Exception e) {
            conn.setHoldingsJson("[]");
        }
        connectionRepository.save(conn);

        List<RobinhoodRhCryptoHoldingDto> holdings = parseHoldings(payload.path("holdings"));
        BigDecimal total = decimalOrZero(payload.path("total_value"));
        List<String> warnings = new ArrayList<>();
        JsonNode warningsNode = payload.get("warnings");
        if (warningsNode != null && warningsNode.isArray()) {
            warningsNode.forEach(n -> {
                if (n != null && !n.asText("").isBlank()) {
                    warnings.add(n.asText());
                }
            });
        }
        return new RobinhoodCryptoTradingSyncResultDto(
                ok,
                message,
                conn.getAccountNumber() == null ? "" : conn.getAccountNumber(),
                total,
                holdings,
                List.copyOf(warnings));
    }

    List<RobinhoodRhCryptoHoldingDto> parseHoldings(JsonNode holdingsNode) {
        if (holdingsNode == null || holdingsNode.isNull()) {
            return List.of();
        }
        try {
            List<RobinhoodRhCryptoHoldingDto> raw =
                    objectMapper.convertValue(holdingsNode, new TypeReference<>() {});
            return raw == null ? List.of() : raw;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void markSyncError(RobinhoodCryptoTradingConnection conn, String message) {
        conn.setLastSyncAt(Instant.now());
        conn.setLastSyncStatus("error");
        conn.setLastSyncMessage(truncate(message));
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);
    }

    private RobinhoodCryptoTradingStatusDto toStatusDto(RobinhoodCryptoTradingConnection conn) {
        return new RobinhoodCryptoTradingStatusDto(
                true,
                agenticProps.serviceConfigured(),
                true,
                maskAccount(conn.getAccountNumber()),
                conn.getConnectedAt(),
                conn.getLastSyncAt(),
                conn.getLastSyncStatus() == null ? "" : conn.getLastSyncStatus(),
                conn.getLastSyncMessage() == null ? "" : conn.getLastSyncMessage());
    }

    private RobinhoodCryptoTradingStatusDto emptyStatus() {
        return new RobinhoodCryptoTradingStatusDto(
                true,
                agenticProps.serviceConfigured(),
                false,
                "",
                null,
                null,
                "",
                "");
    }

    private void requireSidecar() {
        if (!agenticProps.serviceConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Robinhood sidecar is not configured on this server");
        }
    }

    private static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 4) {
            return "••••" + trimmed;
        }
        return "••••" + trimmed.substring(trimmed.length() - 4);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String text = v.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal decimalOrZero(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (node.isNumber()) {
            return node.decimalValue().setScale(2, RoundingMode.HALF_UP);
        }
        String text = node.asText("").trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v;
    }
}
