package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
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
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticOrderService {

    private static final String STATUS_PENDING = "pending_approval";
    private static final String STATUS_PLACED = "placed";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_FAILED = "failed";

    private final RobinhoodAgenticProperties props;
    private final CurrentUserService currentUser;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticSettingsRepository settingsRepository;
    private final RobinhoodAgenticOrderRepository orderRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public RobinhoodAgenticSettingsDto settings() {
        requireFeature();
        long uid = currentUser.requireUserId();
        RobinhoodAgenticSettings row = settingsRepository
                .findByOwnerUserId(uid)
                .orElseGet(this::defaultSettingsRow);
        return toSettingsDto(row);
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
        if (request.requireApproval() != null) {
            row.setRequireApproval(request.requireApproval());
        }
        row.setMaxOrderNotional(request.maxOrderNotional());
        row.setAllowedSymbols(normalizeSymbols(request.allowedSymbols()));
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
        return toSettingsDto(row);
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
        requireFeature();
        validateOrderRequest(request);
        long uid = currentUser.requireUserId();
        RobinhoodAgenticConnection conn = requireConnection(uid);
        RobinhoodAgenticSettings settings = effectiveSettings(uid);
        validateSymbolAllowed(request.symbol(), settings);

        RobinhoodAgenticOrder order = new RobinhoodAgenticOrder();
        order.setOwnerUserId(uid);
        order.setSymbol(request.symbol().trim().toUpperCase());
        order.setSide(request.side().trim().toLowerCase());
        order.setOrderType(request.type() == null || request.type().isBlank() ? "market" : request.type().trim().toLowerCase());
        order.setQuantity(request.quantity());
        order.setAmount(request.amount());
        order.setLimitPrice(request.limitPrice());
        order.setTimeInForce(request.timeInForce());
        order.setAccountNumber(conn.getAgenticAccountNumber());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

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

            if (settings.isRequireApproval()) {
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
        return toOrderDto(order);
    }

    @Transactional
    public RobinhoodAgenticOrderDto approveOrder(long orderId) {
        requireFeature();
        requireExecutionEnabled();
        long uid = currentUser.requireUserId();
        RobinhoodAgenticConnection conn = requireConnection(uid);
        RobinhoodAgenticOrder order = orderRepository
                .findByIdAndOwnerUserId(orderId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }
        RobinhoodAgenticSettings settings = effectiveSettings(uid);
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
    public RobinhoodAgenticOrderDto rejectOrder(long orderId) {
        requireFeature();
        long uid = currentUser.requireUserId();
        RobinhoodAgenticOrder order = orderRepository
                .findByIdAndOwnerUserId(orderId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not pending approval");
        }
        order.setStatus(STATUS_REJECTED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return toOrderDto(order);
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
        RobinhoodAgenticSettings s = new RobinhoodAgenticSettings();
        s.setRequireApproval(true);
        s.setMaxOrderNotional(props.defaultMaxOrderNotional());
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
                row.isRequireApproval(),
                row.getMaxOrderNotional() != null ? row.getMaxOrderNotional() : props.defaultMaxOrderNotional(),
                row.getAllowedSymbols() == null ? "" : row.getAllowedSymbols(),
                row.getUpdatedAt());
    }

    private RobinhoodAgenticOrderDto toOrderDto(RobinhoodAgenticOrder order) {
        return new RobinhoodAgenticOrderDto(
                order.getId(),
                order.getStatus(),
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
