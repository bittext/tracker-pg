package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.domain.RobinhoodAgenticSyncLog;
import com.svp.tracker.finance.dto.RobinhoodAgenticPositionDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticPositionsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticStatusDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticTokensRequestDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticPositionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSyncLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticService {

    private final RobinhoodAgenticProperties props;
    private final CurrentUserService currentUser;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticPositionRepository positionRepository;
    private final RobinhoodAgenticSyncLogRepository syncLogRepository;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticTokenCrypto tokenCrypto;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public RobinhoodAgenticStatusDto status() {
        long uid = currentUser.requireUserId();
        return connectionRepository
                .findByOwnerUserId(uid)
                .map(conn -> new RobinhoodAgenticStatusDto(
                        props.enabled(),
                        props.serviceConfigured(),
                        true,
                        maskAccount(conn.getAgenticAccountNumber()),
                        conn.getAgenticNickname(),
                        conn.getConnectedAt(),
                        conn.getLastSyncAt(),
                        conn.getLastSyncStatus(),
                        conn.getLastSyncMessage(),
                        (int) positionRepository
                                .findByOwnerUserIdOrderByPositionTypeAscSymbolAscChainSymbolAsc(uid)
                                .size()))
                .orElseGet(() -> new RobinhoodAgenticStatusDto(
                        props.enabled(),
                        props.serviceConfigured(),
                        false,
                        "",
                        "",
                        null,
                        null,
                        "",
                        "",
                        0));
    }

    @Transactional
    public RobinhoodAgenticStatusDto saveTokens(RobinhoodAgenticTokensRequestDto request) {
        requireFeature();
        if (request.accessToken() == null || request.accessToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessToken is required");
        }
        long uid = currentUser.requireUserId();
        RobinhoodAgenticConnection conn = connectionRepository
                .findByOwnerUserId(uid)
                .orElseGet(RobinhoodAgenticConnection::new);
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
        log.info("Robinhood Agentic tokens saved for user {}", uid);
        return status();
    }

    @Transactional
    public void disconnect() {
        long uid = currentUser.requireUserId();
        positionRepository.deleteAllByOwnerUserId(uid);
        connectionRepository.deleteByOwnerUserId(uid);
        log.info("Robinhood Agentic disconnected for user {}", uid);
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticPositionsDto positions() {
        long uid = currentUser.requireUserId();
        String portfolioJson = connectionRepository
                .findByOwnerUserId(uid)
                .map(RobinhoodAgenticConnection::getPortfolioJson)
                .orElse("");
        List<RobinhoodAgenticPositionDto> rows = positionRepository
                .findByOwnerUserIdOrderByPositionTypeAscSymbolAscChainSymbolAsc(uid)
                .stream()
                .map(this::toPositionDto)
                .toList();
        return new RobinhoodAgenticPositionsDto(rows, portfolioJson == null ? "" : portfolioJson);
    }

    @Transactional
    public RobinhoodAgenticSyncResultDto syncNow() {
        long uid = currentUser.requireUserId();
        RobinhoodAgenticConnection conn = connectionRepository
                .findByOwnerUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Connect Robinhood Agentic tokens first"));
        return runSync(conn);
    }

    private RobinhoodAgenticSyncResultDto runSync(RobinhoodAgenticConnection conn) {
        requireFeature();
        Instant started = Instant.now();
        RobinhoodAgenticSyncLog logRow = new RobinhoodAgenticSyncLog();
        logRow.setOwnerUserId(conn.getOwnerUserId());
        logRow.setStartedAt(started);
        try {
            String accessToken = tokenCrypto.open(conn.getAccessToken());
            JsonNode result = sidecarClient.sync(accessToken, props.syncDefaultAccount());
            if (!result.path("ok").asBoolean(false)) {
                throw new IllegalStateException("Sidecar sync returned ok=false");
            }
            persistSyncResult(conn, result, started);
            logRow.setStatus("ok");
            logRow.setAccountsSynced(result.path("accounts").size());
            logRow.setMessage("Synced " + result.path("positions").size() + " position row(s)");
            logRow.setFinishedAt(Instant.now());
            syncLogRepository.save(logRow);
            int count = (int) positionRepository
                    .findByOwnerUserIdOrderByPositionTypeAscSymbolAscChainSymbolAsc(conn.getOwnerUserId())
                    .size();
            return new RobinhoodAgenticSyncResultDto(
                    true, conn.getLastSyncAt(), conn.getLastSyncMessage(), count, logRow.getAccountsSynced());
        } catch (Exception e) {
            conn.setLastSyncAt(started);
            conn.setLastSyncStatus("error");
            conn.setLastSyncMessage(truncate(e.getMessage(), 500));
            conn.setUpdatedAt(Instant.now());
            connectionRepository.save(conn);
            logRow.setStatus("error");
            logRow.setMessage(truncate(e.getMessage(), 500));
            logRow.setFinishedAt(Instant.now());
            syncLogRepository.save(logRow);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    private void persistSyncResult(RobinhoodAgenticConnection conn, JsonNode result, Instant syncedAt) throws Exception {
        String agenticNum = textOrNull(result.get("agentic_account_number"));
        conn.setAgenticAccountNumber(agenticNum);
        conn.setAgenticNickname(textOrNull(result.get("agentic_nickname")));
        conn.setPortfolioJson(objectMapper.writeValueAsString(result.get("portfolios")));
        conn.setLastSyncAt(syncedAt);
        conn.setLastSyncStatus("ok");
        conn.setLastSyncMessage("Synced " + result.path("positions").size() + " position row(s)");
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);

        positionRepository.deleteAllByOwnerUserId(conn.getOwnerUserId());
        List<RobinhoodAgenticPosition> batch = new ArrayList<>();
        for (JsonNode row : result.withArray("positions")) {
            String positionKey = textOrNull(row.get("position_key"));
            String symbol = textOrNull(row.get("symbol"));
            if (positionKey == null || positionKey.isBlank()) {
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                positionKey = symbol.trim().toUpperCase();
            }
            String positionType = textOrNull(row.get("position_type"));
            if (positionType == null || positionType.isBlank()) {
                positionType = "equity";
            }
            RobinhoodAgenticPosition pos = new RobinhoodAgenticPosition();
            pos.setOwnerUserId(conn.getOwnerUserId());
            pos.setAccountNumber(textOrNull(row.get("account_number")));
            pos.setPositionType(positionType);
            pos.setPositionKey(positionKey);
            pos.setSymbol(symbol == null ? positionKey : symbol.trim().toUpperCase());
            pos.setChainSymbol(textOrNull(row.get("chain_symbol")));
            pos.setOptionType(textOrNull(row.get("option_type")));
            pos.setStrikePrice(decimalOrNull(row.get("strike_price")));
            pos.setExpirationDate(localDateOrNull(row.get("expiration_date")));
            pos.setQuantity(decimalOrNull(row.get("quantity")));
            pos.setAverageBuyPrice(decimalOrNull(row.get("average_buy_price")));
            pos.setMarketValue(decimalOrNull(row.get("market_value")));
            pos.setSyncedAt(syncedAt);
            batch.add(pos);
        }
        positionRepository.saveAll(batch);
    }

    private RobinhoodAgenticPositionDto toPositionDto(RobinhoodAgenticPosition p) {
        return new RobinhoodAgenticPositionDto(
                maskAccount(p.getAccountNumber()),
                p.getPositionType(),
                p.getPositionKey(),
                p.getSymbol(),
                p.getChainSymbol(),
                p.getOptionType(),
                p.getStrikePrice(),
                p.getExpirationDate(),
                p.getQuantity(),
                p.getAverageBuyPrice(),
                p.getMarketValue(),
                p.getSyncedAt());
    }

    private void requireFeature() {
        if (!props.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Robinhood Agentic is disabled");
        }
        if (!props.serviceConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Robinhood Agentic sidecar not configured");
        }
    }

    static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber == null ? "" : accountNumber;
        }
        return "•••" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String t = node.asText();
        return t.isBlank() ? null : t;
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }

    private static LocalDate localDateOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String t = node.asText().trim();
        if (t.isBlank()) {
            return null;
        }
        if (t.length() >= 10) {
            return LocalDate.parse(t.substring(0, 10));
        }
        return LocalDate.parse(t);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
