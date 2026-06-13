package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticOrder;
import com.svp.tracker.finance.domain.RobinhoodAgenticSettings;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderRequestDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrdersDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsRequestDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticOrderService {

    static final String SOURCE_MANUAL = "manual";
    static final String SOURCE_AUTO = "auto";

    private static final String STATUS_PENDING = "pending_approval";
    private static final String STATUS_PLACED = "placed";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_FAILED = "failed";

    private final RobinhoodAgenticProperties props;
    private final RobinhoodAgenticAutoTradeProperties autoTradeProps;
    private final CurrentUserService currentUser;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticSettingsRepository settingsRepository;
    private final RobinhoodAgenticOrderRepository orderRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticAdminDefaultsService adminDefaultsService;
    private final RobinhoodAgenticApprovalNotificationService approvalNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Legacy static defaults; prefer {@link RobinhoodAgenticAdminDefaultsService#newUserSettingsTemplate()}. */
    static RobinhoodAgenticSettings defaultSettingsTemplate() {
        RobinhoodAgenticSettings s = new RobinhoodAgenticSettings();
        s.setRequireApproval(true);
        s.setAutoTradeRequireApproval(true);
        s.setAutoTradeMinPositivityBuy(new BigDecimal("15.00"));
        s.setAutoTradeMaxPositivitySell(new BigDecimal("-15.00"));
        s.setAutoTradeMinSpikeZ(new BigDecimal("1.5000"));
        s.setAutoTradeMinMentions24h(5);
        s.setAutoTradeOrderQuantity(BigDecimal.ONE);
        s.setAutoTradeMaxTradesPerDay(3);
        s.setAutoTradeCooldownMinutes(60);
        s.setAutoTradeMarketHoursOnly(true);
        return s;
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticSettingsDto settings() {
        requireFeature();
        long uid = currentUser.requireUserId();
        RobinhoodAgenticSettings row = settingsRepository
                .findByOwnerUserId(uid)
                .orElseGet(this::defaultSettingsRow);
        return toSettingsDto(row);
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticSettingsDto settingsForUser(long ownerUserId) {
        requireFeature();
        RobinhoodAgenticSettings row = settingsRepository
                .findByOwnerUserId(ownerUserId)
                .orElseGet(this::defaultSettingsRow);
        return toSettingsDto(row);
    }

    @Transactional
    public RobinhoodAgenticSettingsDto saveSettingsForUser(
            long ownerUserId, RobinhoodAgenticSettingsRequestDto request) {
        requireFeature();
        RobinhoodAgenticSettings row = settingsRepository
                .findByOwnerUserId(ownerUserId)
                .orElseGet(() -> {
                    RobinhoodAgenticSettings s = defaultSettingsRow();
                    s.setOwnerUserId(ownerUserId);
                    return s;
                });
        applySettingsRequest(row, request);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
        return toSettingsDto(row);
    }

    @Transactional
    public RobinhoodAgenticOrderDto approveOrderForUser(long ownerUserId, long orderId) {
        requireFeature();
        requireExecutionEnabled();
        RobinhoodAgenticConnection conn = requireConnection(ownerUserId);
        RobinhoodAgenticOrder order = orderRepository
                .findByIdAndOwnerUserId(orderId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }
        RobinhoodAgenticSettings settings = effectiveSettings(ownerUserId);
        validateSymbolAllowed(order.getSymbol(), settings);
        validateNotional(order.getEstimatedNotional(), settings);
        RobinhoodAgenticOrderRequestDto request = toRequestDto(order);
        try {
            placeReviewedOrder(conn, order, request);
        } catch (Exception e) {
            order.setStatus(STATUS_FAILED);
            order.setErrorMessage(truncate(e.getMessage(), 500));
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
        orderRepository.save(order);
        return toOrderDto(order);
    }

    @Transactional
    public RobinhoodAgenticOrderDto rejectOrderForUser(long ownerUserId, long orderId) {
        requireFeature();
        RobinhoodAgenticOrder order = orderRepository
                .findByIdAndOwnerUserId(orderId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }
        order.setStatus(STATUS_REJECTED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return toOrderDto(order);
    }

    @Transactional
    public RobinhoodAgenticSettingsDto saveSettings(RobinhoodAgenticSettingsRequestDto request) {
        requireFeature();
        long uid = currentUser.requireUserId();
        RobinhoodAgenticSettings row = settingsRepository
                .findByOwnerUserId(uid)
                .orElseGet(() -> {
                    RobinhoodAgenticSettings s = defaultSettingsRow();
                    s.setOwnerUserId(uid);
                    return s;
                });
        applySettingsRequest(row, request);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
        return toSettingsDto(row);
    }

    private void applySettingsRequest(RobinhoodAgenticSettings row, RobinhoodAgenticSettingsRequestDto request) {
        if (request.requireApproval() != null) {
            row.setRequireApproval(request.requireApproval());
        }
        row.setMaxOrderNotional(request.maxOrderNotional());
        row.setAllowedSymbols(normalizeSymbols(request.allowedSymbols()));
        if (request.autoTradeEnabled() != null) {
            row.setAutoTradeEnabled(request.autoTradeEnabled());
        }
        if (request.autoTradeKillSwitch() != null) {
            row.setAutoTradeKillSwitch(request.autoTradeKillSwitch());
        }
        if (request.autoTradeRequireApproval() != null) {
            row.setAutoTradeRequireApproval(request.autoTradeRequireApproval());
        }
        if (request.autoTradeMinPositivityBuy() != null) {
            row.setAutoTradeMinPositivityBuy(request.autoTradeMinPositivityBuy());
        }
        if (request.autoTradeMaxPositivitySell() != null) {
            row.setAutoTradeMaxPositivitySell(request.autoTradeMaxPositivitySell());
        }
        if (request.autoTradeMinSpikeZ() != null) {
            row.setAutoTradeMinSpikeZ(request.autoTradeMinSpikeZ());
        }
        if (request.autoTradeMinMentions24h() != null) {
            row.setAutoTradeMinMentions24h(Math.max(1, request.autoTradeMinMentions24h()));
        }
        if (request.autoTradeOrderQuantity() != null) {
            row.setAutoTradeOrderQuantity(request.autoTradeOrderQuantity());
        }
        if (request.autoTradeMaxTradesPerDay() != null) {
            row.setAutoTradeMaxTradesPerDay(Math.max(1, request.autoTradeMaxTradesPerDay()));
        }
        row.setAutoTradeMaxDailyNotional(request.autoTradeMaxDailyNotional());
        if (request.autoTradeCooldownMinutes() != null) {
            row.setAutoTradeCooldownMinutes(Math.max(1, request.autoTradeCooldownMinutes()));
        }
        if (request.autoTradeMarketHoursOnly() != null) {
            row.setAutoTradeMarketHoursOnly(request.autoTradeMarketHoursOnly());
        }
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticOrdersDto orders() {
        requireFeature();
        long uid = currentUser.requireUserId();
        List<RobinhoodAgenticOrderDto> rows = orderRepository.findByOwnerUserIdOrderByCreatedAtDesc(uid).stream()
                .map(this::toOrderDto)
                .toList();
        return new RobinhoodAgenticOrdersDto(rows);
    }

    @Transactional
    public RobinhoodAgenticOrderDto reviewOrder(RobinhoodAgenticOrderRequestDto request) {
        long uid = currentUser.requireUserId();
        RobinhoodAgenticOrder order = reviewOrderForUser(uid, request, SOURCE_MANUAL, null, false);
        return toOrderDto(order);
    }

    /** Used by AI auto-trade scheduler; returns persisted order entity. */
    @Transactional
    public RobinhoodAgenticOrder reviewOrderForUser(
            long ownerUserId,
            RobinhoodAgenticOrderRequestDto request,
            String source,
            String autoSignalJson,
            boolean autoTradePolicy) {
        requireFeature();
        validateOrderRequest(request);
        RobinhoodAgenticConnection conn = requireConnection(ownerUserId);
        RobinhoodAgenticSettings settings = effectiveSettings(ownerUserId);
        validateSymbolAllowed(request.symbol(), settings);

        RobinhoodAgenticOrder order = new RobinhoodAgenticOrder();
        order.setOwnerUserId(ownerUserId);
        order.setSource(source == null ? SOURCE_MANUAL : source);
        order.setAutoSignalJson(autoSignalJson);
        order.setSymbol(request.symbol().trim().toUpperCase(Locale.ROOT));
        order.setSide(request.side().trim().toLowerCase(Locale.ROOT));
        order.setOrderType(request.type() == null || request.type().isBlank() ? "market" : request.type().trim().toLowerCase());
        order.setQuantity(request.quantity());
        order.setAmount(request.amount());
        order.setLimitPrice(request.limitPrice());
        order.setTimeInForce(request.timeInForce());
        order.setAccountNumber(conn.getAgenticAccountNumber());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        boolean requireApproval = autoTradePolicy ? settings.isAutoTradeRequireApproval() : settings.isRequireApproval();

        try {
            JsonNode result = tokenService.withFreshToken(
                    conn, token -> sidecarClient.reviewOrder(token, request, conn.getAgenticAccountNumber()));
            if (!result.path("ok").asBoolean(false)) {
                throw new IllegalStateException("Order review returned ok=false");
            }
            order.setReviewJson(objectMapper.writeValueAsString(result.get("review")));
            order.setReviewedAt(Instant.now());
            BigDecimal notional = decimalOrNull(result.get("estimated_notional"));
            order.setEstimatedNotional(notional);
            validateNotional(notional, settings);
            String acct = result.path("account_number").asText(null);
            if (acct != null && !acct.isBlank()) {
                order.setAccountNumber(acct);
            }

            if (requireApproval) {
                order.setStatus(STATUS_PENDING);
            } else {
                requireExecutionEnabled();
                placeReviewedOrder(conn, order, request);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            order.setStatus(STATUS_FAILED);
            order.setErrorMessage(truncate(e.getMessage(), 500));
            orderRepository.save(order);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }

        orderRepository.save(order);
        if (STATUS_PENDING.equals(order.getStatus())) {
            notifyPendingApprovalAfterCommit(order);
        }
        return order;
    }

    @Transactional
    public RobinhoodAgenticOrderDto approveOrder(long orderId) {
        requireFeature();
        requireExecutionEnabled();
        long uid = currentUser.requireUserId();
        return approveOrderForUser(uid, orderId);
    }

    @Transactional
    public RobinhoodAgenticOrderDto rejectOrder(long orderId) {
        requireFeature();
        long uid = currentUser.requireUserId();
        return rejectOrderForUser(uid, orderId);
    }

    private void notifyPendingApprovalAfterCommit(RobinhoodAgenticOrder order) {
        RobinhoodAgenticOrder snapshot = order;
        runAfterCommit(() -> approvalNotificationService.notifyPendingApproval(snapshot));
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void placeReviewedOrder(
            RobinhoodAgenticConnection conn, RobinhoodAgenticOrder order, RobinhoodAgenticOrderRequestDto request)
            throws Exception {
        JsonNode result = tokenService.withFreshToken(
                conn, token -> sidecarClient.placeOrder(token, request, order.getAccountNumber()));
        if (!result.path("ok").asBoolean(false)) {
            throw new IllegalStateException("Order placement returned ok=false");
        }
        order.setPlaceJson(objectMapper.writeValueAsString(result.get("result")));
        order.setRobinhoodOrderId(result.path("order_id").asText(null));
        order.setStatus(STATUS_PLACED);
        order.setPlacedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setErrorMessage(null);
    }

    private RobinhoodAgenticSettings effectiveSettings(long uid) {
        return settingsRepository.findByOwnerUserId(uid).orElseGet(this::defaultSettingsRow);
    }

    private RobinhoodAgenticSettings defaultSettingsRow() {
        RobinhoodAgenticSettings s = adminDefaultsService.newUserSettingsTemplate();
        if (s.getMaxOrderNotional() == null) {
            s.setMaxOrderNotional(props.defaultMaxOrderNotional());
        }
        return s;
    }

    private RobinhoodAgenticConnection requireConnection(long uid) {
        return connectionRepository
                .findByOwnerUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Connect Robinhood Agentic tokens first"));
    }

    private void validateOrderRequest(RobinhoodAgenticOrderRequestDto request) {
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        if (request.side() == null || !Set.of("buy", "sell").contains(request.side().trim().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "side must be buy or sell");
        }
        String type = request.type() == null ? "market" : request.type().trim().toLowerCase();
        if (!Set.of("market", "limit").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be market or limit");
        }
        if ("limit".equals(type) && request.limitPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit_price is required for limit orders");
        }
        if (request.quantity() == null && request.amount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity or amount is required");
        }
    }

    private void validateSymbolAllowed(String symbol, RobinhoodAgenticSettings settings) {
        String allowed = settings.getAllowedSymbols();
        if (allowed == null || allowed.isBlank()) {
            return;
        }
        Set<String> whitelist = Arrays.stream(allowed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!whitelist.isEmpty() && !whitelist.contains(symbol.trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Symbol " + symbol + " is not in allowed_symbols whitelist");
        }
    }

    private void validateNotional(BigDecimal notional, RobinhoodAgenticSettings settings) {
        if (notional == null) {
            return;
        }
        BigDecimal cap = settings.getMaxOrderNotional();
        if (cap == null) {
            cap = props.defaultMaxOrderNotional();
        }
        if (cap != null && notional.compareTo(cap) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order notional " + notional + " exceeds max allowed " + cap);
        }
    }

    private void requireFeature() {
        if (!props.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Robinhood Agentic is disabled");
        }
        if (!props.serviceConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Robinhood Agentic sidecar not configured");
        }
    }

    private void requireExecutionEnabled() {
        if (!props.executionEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Robinhood Agentic execution is disabled (TRACKER_FINANCE_ROBINHOOD_AGENTIC_EXECUTION_ENABLED)");
        }
    }

    private static RobinhoodAgenticOrderRequestDto toRequestDto(RobinhoodAgenticOrder order) {
        return new RobinhoodAgenticOrderRequestDto(
                order.getSymbol(),
                order.getSide(),
                order.getOrderType(),
                order.getQuantity(),
                order.getAmount(),
                order.getLimitPrice(),
                order.getTimeInForce());
    }

    private RobinhoodAgenticSettingsDto toSettingsDto(RobinhoodAgenticSettings row) {
        return new RobinhoodAgenticSettingsDto(
                props.executionEnabled(),
                autoTradeProps.enabled(),
                row.isRequireApproval(),
                row.getMaxOrderNotional() != null ? row.getMaxOrderNotional() : props.defaultMaxOrderNotional(),
                row.getAllowedSymbols() == null ? "" : row.getAllowedSymbols(),
                row.isAutoTradeEnabled(),
                row.isAutoTradeKillSwitch(),
                row.isAutoTradeRequireApproval(),
                row.getAutoTradeMinPositivityBuy(),
                row.getAutoTradeMaxPositivitySell(),
                row.getAutoTradeMinSpikeZ(),
                row.getAutoTradeMinMentions24h(),
                row.getAutoTradeOrderQuantity(),
                row.getAutoTradeMaxTradesPerDay(),
                row.getAutoTradeMaxDailyNotional(),
                row.getAutoTradeCooldownMinutes(),
                row.isAutoTradeMarketHoursOnly(),
                row.getAutoTradeLastRunAt(),
                row.getAutoTradeLastRunMessage() == null ? "" : row.getAutoTradeLastRunMessage(),
                row.getUpdatedAt());
    }

    RobinhoodAgenticOrderDto toOrderDto(RobinhoodAgenticOrder order) {
        return new RobinhoodAgenticOrderDto(
                order.getId(),
                order.getStatus(),
                order.getSource() == null ? SOURCE_MANUAL : order.getSource(),
                order.getSymbol(),
                order.getSide(),
                order.getOrderType(),
                order.getQuantity(),
                order.getAmount(),
                order.getLimitPrice(),
                order.getTimeInForce(),
                order.getEstimatedNotional(),
                order.getRobinhoodOrderId(),
                order.getErrorMessage(),
                order.getAutoSignalJson(),
                order.getCreatedAt(),
                order.getReviewedAt(),
                order.getPlacedAt());
    }

    private static String normalizeSymbols(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
