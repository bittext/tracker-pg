package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.domain.RobinhoodAgenticSyncedOrder;
import com.svp.tracker.finance.domain.RobinhoodAgenticSyncLog;
import com.svp.tracker.finance.dto.RobinhoodAgenticPositionDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticPositionsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticStatusDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSyncedOrderDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSyncedOrdersDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticTokensRequestDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticPositionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSyncedOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSyncLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class RobinhoodAgenticService {

    private static final int ORDERS_SYNC_LIMIT = 10;

    private final RobinhoodAgenticProperties props;
    private final CurrentUserService currentUser;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticPositionRepository positionRepository;
    private final RobinhoodAgenticSyncedOrderRepository syncedOrderRepository;
    private final RobinhoodAgenticSyncLogRepository syncLogRepository;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticTokenCrypto tokenCrypto;
    private final RobinhoodAgenticTokenService tokenService;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
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
                        openPositionCount(uid)))
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
        syncedOrderRepository.deleteAllByOwnerUserId(uid);
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
        List<RobinhoodAgenticPositionDto> rows = trimOpenPositions(
                        positionRepository.findByOwnerUserIdOrderByPositionTypeAscSymbolAscChainSymbolAsc(uid))
                .stream()
                .map(this::toPositionDto)
                .toList();
        return new RobinhoodAgenticPositionsDto(rows, portfolioJson == null ? "" : portfolioJson);
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticSyncedOrdersDto syncedOrders() {
        long uid = currentUser.requireUserId();
        List<RobinhoodAgenticSyncedOrderDto> rows = syncedOrderRepository
                .findTop10ByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(uid)
                .stream()
                .map(this::toSyncedOrderDto)
                .toList();
        return new RobinhoodAgenticSyncedOrdersDto(rows);
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

    /** Best-effort live sync for RH Accounts Track refresh; never throws. */
    public List<String> syncLatestForAccountsTrack() {
        if (!props.serviceConfigured()) {
            return List.of("Live sync skipped — Robinhood Agentic sidecar not configured.");
        }
        long uid = currentUser.requireUserId();
        if (connectionRepository.findByOwnerUserId(uid).isEmpty()) {
            return List.of(
                    "Live sync skipped — connect Robinhood Agentic Trading to pull latest holdings and options.");
        }
        if (!props.enabled()) {
            return List.of("Live sync skipped — Robinhood Agentic is disabled.");
        }
        try {
            RobinhoodAgenticSyncResultDto result = syncNow();
            return List.of(result.message());
        } catch (Exception e) {
            log.warn("RH Accounts Track live sync failed for user {}", uid, e);
            return List.of(
                    "Live sync failed ("
                            + truncate(rootCauseMessage(e), 180)
                            + ") — showing last cached snapshot.");
        }
    }

    /** Called by scheduled sync — no current-user context. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncConnection(RobinhoodAgenticConnection conn) {
        runSync(conn);
    }

    /**
     * Sync when the sidecar is reachable; on network/DNS failures log a warning and return false without throwing.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = ResponseStatusException.class)
    public boolean syncConnectionBestEffort(RobinhoodAgenticConnection conn) {
        try {
            runSync(conn);
            return true;
        } catch (ResponseStatusException e) {
            if (RobinhoodAgenticSidecarErrors.isSidecarDown(e) || RobinhoodAgenticSidecarErrors.isUnreachable(e)) {
                log.warn(
                        "Robinhood Agentic sync skipped for user {} at {}: {}",
                        conn.getOwnerUserId(),
                        props.serviceBaseUrl(),
                        e.getReason());
                return false;
            }
            throw e;
        }
    }

    private RobinhoodAgenticSyncResultDto runSync(RobinhoodAgenticConnection conn) {
        requireFeature();
        Instant started = Instant.now();
        RobinhoodAgenticSyncLog logRow = new RobinhoodAgenticSyncLog();
        logRow.setOwnerUserId(conn.getOwnerUserId());
        logRow.setStartedAt(started);
        try {
            JsonNode result = tokenService.syncAllAccounts(conn);
            if (!result.path("ok").asBoolean(false)) {
                throw new IllegalStateException("Sidecar sync returned ok=false");
            }
            persistSyncResult(conn, result, started);
            logRow.setStatus("ok");
            logRow.setAccountsSynced(result.path("accounts").size());
            logRow.setMessage(buildSyncMessage(result));
            logRow.setFinishedAt(Instant.now());
            syncLogRepository.save(logRow);
            int count = openPositionCount(conn.getOwnerUserId());
            int orderCount = (int) syncedOrderRepository
                    .findTop10ByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(conn.getOwnerUserId())
                    .size();
            return new RobinhoodAgenticSyncResultDto(
                    true, conn.getLastSyncAt(), conn.getLastSyncMessage(), count, orderCount, logRow.getAccountsSynced());
        } catch (Exception e) {
            if (RobinhoodAgenticSidecarErrors.isUnreachable(e)) {
                log.warn(
                        "Robinhood Agentic sidecar unreachable for user {} at {}: {}",
                        conn.getOwnerUserId(),
                        props.serviceBaseUrl(),
                        rootCauseMessage(e));
            } else {
                log.error("Robinhood Agentic sync failed for user {}", conn.getOwnerUserId(), e);
            }
            throw new ResponseStatusException(
                    RobinhoodAgenticSidecarErrors.isUnreachable(e)
                            ? HttpStatus.SERVICE_UNAVAILABLE
                            : HttpStatus.BAD_GATEWAY,
                    truncate(rootCauseMessage(e), 500),
                    e);
        }
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private void persistSyncResult(RobinhoodAgenticConnection conn, JsonNode result, Instant syncedAt) throws Exception {
        String agenticNum = textOrNull(result.get("agentic_account_number"));
        conn.setAgenticAccountNumber(agenticNum);
        conn.setAgenticNickname(textOrNull(result.get("agentic_nickname")));
        conn.setPortfolioJson(objectMapper.writeValueAsString(result.get("portfolios")));
        conn.setLastSyncAt(syncedAt);
        conn.setLastSyncStatus("ok");
        conn.setLastSyncMessage(buildSyncMessage(result));
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);

        accountTrackerConfigService.applyRolesFromSync(conn.getOwnerUserId(), result);

        positionRepository.deleteAllByOwnerUserId(conn.getOwnerUserId());
        Map<String, RobinhoodAgenticPosition> byAccountAndKey = new LinkedHashMap<>();
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
            String accountNumber = textOrNull(row.get("account_number"));
            if (accountNumber == null || accountNumber.isBlank()) {
                accountNumber = agenticNum != null ? agenticNum : "";
            }
            RobinhoodAgenticPosition pos = new RobinhoodAgenticPosition();
            pos.setOwnerUserId(conn.getOwnerUserId());
            pos.setAccountNumber(accountNumber);
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
            // Sidecar may emit duplicate option legs (same option_id, long + short); last row wins.
            byAccountAndKey.put(accountNumber + "\u0000" + positionKey, pos);
        }
        positionRepository.saveAll(trimOpenPositions(List.copyOf(byAccountAndKey.values())));

        syncedOrderRepository.deleteAllByOwnerUserId(conn.getOwnerUserId());
        Map<String, RobinhoodAgenticSyncedOrder> byAccountAndRhOrder = new LinkedHashMap<>();
        for (JsonNode row : result.withArray("orders")) {
            String rhOrderId = textOrNull(row.get("robinhood_order_id"));
            if (rhOrderId == null || rhOrderId.isBlank()) {
                continue;
            }
            rhOrderId = rhOrderId.trim();
            String symbol = textOrNull(row.get("symbol"));
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            String accountNumber = textOrNull(row.get("account_number"));
            if (accountNumber == null || accountNumber.isBlank()) {
                accountNumber = agenticNum != null ? agenticNum : "";
            }
            accountNumber = accountNumber.trim();
            RobinhoodAgenticSyncedOrder order = new RobinhoodAgenticSyncedOrder();
            order.setOwnerUserId(conn.getOwnerUserId());
            order.setAccountNumber(accountNumber);
            order.setRobinhoodOrderId(rhOrderId);
            order.setSymbol(symbol.trim().toUpperCase());
            order.setSide(textOrNull(row.get("side")));
            order.setOrderType(textOrNull(row.get("order_type")));
            order.setQuantity(decimalOrNull(row.get("quantity")));
            order.setLimitPrice(decimalOrNull(row.get("limit_price")));
            order.setAveragePrice(decimalOrNull(row.get("average_price")));
            order.setState(textOrNull(row.get("state")));
            order.setCreatedAtRh(instantOrNull(row.get("created_at")));
            order.setUpdatedAtRh(instantOrNull(row.get("updated_at")));
            order.setSyncedAt(syncedAt);
            byAccountAndRhOrder.put(accountNumber + "\u0000" + rhOrderId, order);
        }
        syncedOrderRepository.saveAll(byAccountAndRhOrder.values());
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

    private RobinhoodAgenticSyncedOrderDto toSyncedOrderDto(RobinhoodAgenticSyncedOrder o) {
        return new RobinhoodAgenticSyncedOrderDto(
                maskAccount(o.getAccountNumber()),
                o.getRobinhoodOrderId(),
                o.getSymbol(),
                o.getSide(),
                o.getOrderType(),
                o.getQuantity(),
                o.getLimitPrice(),
                o.getAveragePrice(),
                o.getState(),
                o.getCreatedAtRh(),
                o.getUpdatedAtRh(),
                o.getSyncedAt());
    }

    private static String buildSyncMessage(JsonNode result) {
        int positionCount = result.path("positions").size();
        int orderCount = result.path("orders").size();
        StringBuilder msg = new StringBuilder("Synced ")
                .append(positionCount)
                .append(" open position(s) and ")
                .append(orderCount)
                .append(" recent order(s)");
        for (JsonNode warning : result.withArray("warnings")) {
            if (!warning.isNull() && !warning.asText().isBlank()) {
                msg.append(". ").append(warning.asText());
            }
        }
        return truncate(msg.toString(), 500);
    }

    private int openPositionCount(long ownerUserId) {
        return trimOpenPositions(
                        positionRepository.findByOwnerUserIdOrderByPositionTypeAscSymbolAscChainSymbolAsc(
                                ownerUserId))
                .size();
    }

    private static boolean isOpenPosition(BigDecimal quantity) {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) != 0;
    }

    private static BigDecimal positionRankValue(RobinhoodAgenticPosition position) {
        if (position.getMarketValue() != null) {
            return position.getMarketValue().abs();
        }
        if (position.getQuantity() != null && position.getAverageBuyPrice() != null) {
            return position.getQuantity().multiply(position.getAverageBuyPrice()).abs();
        }
        return BigDecimal.ZERO;
    }

    private List<RobinhoodAgenticPosition> trimOpenPositions(List<RobinhoodAgenticPosition> positions) {
        return positions.stream()
                .filter(p -> isOpenPosition(p.getQuantity()))
                .sorted(Comparator.comparing(RobinhoodAgenticService::positionRankValue).reversed())
                .toList();
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

    private static Instant instantOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String t = node.asText().trim();
        if (t.isBlank()) {
            return null;
        }
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
