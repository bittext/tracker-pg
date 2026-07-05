package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoAutoTradeProperties;
import com.svp.tracker.finance.domain.RobinhoodCryptoOrder;
import com.svp.tracker.finance.domain.RobinhoodCryptoTradingConnection;
import com.svp.tracker.finance.domain.RobinhoodCryptoTradingSettings;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeSettingsDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeSettingsRequestDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoOrderDto;
import com.svp.tracker.finance.repository.RobinhoodCryptoOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodCryptoTradingConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodCryptoTradingSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhCryptoOrderService {

    static final String SOURCE_MANUAL = "manual";
    static final String SOURCE_AUTO = "auto";

    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_PLACED = "placed";
    private static final String STATUS_SUBMITTED = "submitted";

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhCryptoAutoTradeProperties autoTradeProps;
    private final CurrentUserService currentUser;
    private final RobinhoodCryptoTradingConnectionRepository connectionRepository;
    private final RobinhoodCryptoTradingSettingsRepository settingsRepository;
    private final RobinhoodCryptoOrderRepository orderRepository;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticTokenCrypto tokenCrypto;
    private final RobinhoodCryptoTradingService cryptoTradingService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public RobinhoodRhCryptoAutoTradeSettingsDto autoTradeSettings() {
        requireSidecar();
        long uid = currentUser.requireUserId();
        RobinhoodCryptoTradingSettings row = settingsRepository
                .findByOwnerUserId(uid)
                .orElseGet(this::defaultSettingsTemplate);
        return toSettingsDto(row, cryptoTradingService.isConnected(uid));
    }

    @Transactional
    public RobinhoodRhCryptoAutoTradeSettingsDto saveAutoTradeSettings(
            RobinhoodRhCryptoAutoTradeSettingsRequestDto request) {
        requireSidecar();
        long uid = currentUser.requireUserId();
        RobinhoodCryptoTradingSettings row = settingsRepository
                .findByOwnerUserId(uid)
                .orElseGet(() -> {
                    RobinhoodCryptoTradingSettings s = defaultSettingsTemplate();
                    s.setOwnerUserId(uid);
                    return s;
                });
        applySettingsRequest(row, request);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
        return toSettingsDto(row, cryptoTradingService.isConnected(uid));
    }

    @Transactional(readOnly = true)
    public List<RobinhoodRhCryptoOrderDto> recentOrders() {
        long uid = currentUser.requireUserId();
        return orderRepository.findTop30ByOwnerUserIdOrderByCreatedAtDesc(uid).stream()
                .map(this::toOrderDto)
                .toList();
    }

    /** Places a market order for auto-trade (no approval queue). */
    @Transactional
    public RobinhoodCryptoOrder placeAutoMarketOrder(
            long ownerUserId,
            String assetSymbol,
            String tradingPair,
            String side,
            BigDecimal quoteAmount,
            BigDecimal assetQuantity,
            String signalJson) {
        requireSidecar();
        RobinhoodCryptoTradingConnection conn = connectionRepository
                .findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Crypto Trading API not connected"));
        String accountNumber = conn.getAccountNumber();
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Account number missing — run a crypto sync first");
        }

        RobinhoodCryptoOrder order = new RobinhoodCryptoOrder();
        order.setOwnerUserId(ownerUserId);
        order.setStatus(STATUS_SUBMITTED);
        order.setSymbol(assetSymbol.toUpperCase(Locale.ROOT));
        order.setTradingPair(tradingPair);
        order.setSide(side.toLowerCase(Locale.ROOT));
        order.setOrderType("market");
        order.setQuoteAmount(quoteAmount);
        order.setAssetQuantity(assetQuantity);
        order.setEstimatedNotional(quoteAmount);
        order.setSource(SOURCE_AUTO);
        order.setAutoSignalJson(signalJson);
        order.setClientOrderId(UUID.randomUUID().toString());
        order.setCreatedAt(Instant.now());
        orderRepository.save(order);

        try {
            String apiKey = tokenCrypto.open(conn.getApiKeyEnc());
            String privateKey = tokenCrypto.open(conn.getPrivateKeyEnc());
            JsonNode result = sidecarClient.cryptoPlaceOrder(
                    apiKey,
                    privateKey,
                    accountNumber,
                    tradingPair,
                    side,
                    assetQuantity,
                    quoteAmount,
                    order.getClientOrderId());
            order.setPlaceJson(objectMapper.writeValueAsString(result));
            boolean ok = result.path("ok").asBoolean(false);
            String rhOrderId = textOrNull(result, "order_id");
            String state = textOrNull(result, "state");
            if (ok) {
                order.setStatus(state != null && !state.isBlank() ? state : STATUS_PLACED);
                order.setRobinhoodOrderId(rhOrderId);
                order.setPlacedAt(Instant.now());
            } else {
                order.setStatus(STATUS_FAILED);
                order.setErrorMessage(textOrNull(result, "message"));
            }
        } catch (RobinhoodAgenticUnauthorizedException e) {
            order.setStatus(STATUS_FAILED);
            order.setErrorMessage(truncate(
                    "Unauthorized — API key may lack place-order permission. Reconnect with a trade-enabled key.", 500));
        } catch (Exception e) {
            order.setStatus(STATUS_FAILED);
            order.setErrorMessage(truncate(e.getMessage(), 500));
            log.warn("Crypto auto-trade order failed for user {} {}: {}", ownerUserId, assetSymbol, e.getMessage());
        }
        orderRepository.save(order);
        return order;
    }

    public boolean isSymbolAllowed(String assetSymbol, RobinhoodCryptoTradingSettings settings) {
        List<String> allowed = parseAllowedSymbols(settings.getAllowedSymbolsJson());
        if (allowed.isEmpty()) {
            return true;
        }
        String normalized = RobinhoodRhCryptoSymbolMap.toAssetCode(assetSymbol);
        return allowed.stream().anyMatch(s -> s.equalsIgnoreCase(normalized));
    }

    static List<String> parseAllowedSymbols(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = new ObjectMapper().readValue(json, new TypeReference<>() {});
            if (list == null) {
                return List.of();
            }
            return list.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    static String serializeAllowedSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return "[]";
        }
        List<String> normalized = symbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        try {
            return new ObjectMapper().writeValueAsString(normalized);
        } catch (Exception e) {
            return "[\"BTC\",\"ETH\"]";
        }
    }

    RobinhoodCryptoTradingSettings defaultSettingsTemplate() {
        RobinhoodCryptoTradingSettings s = new RobinhoodCryptoTradingSettings();
        s.setAutoTradeEnabled(false);
        s.setAutoTradeKillSwitch(false);
        return s;
    }

    private void applySettingsRequest(
            RobinhoodCryptoTradingSettings row, RobinhoodRhCryptoAutoTradeSettingsRequestDto request) {
        if (request.autoTradeEnabled() != null) {
            row.setAutoTradeEnabled(request.autoTradeEnabled());
        }
        if (request.autoTradeKillSwitch() != null) {
            row.setAutoTradeKillSwitch(request.autoTradeKillSwitch());
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
            row.setAutoTradeMinMentions24h(request.autoTradeMinMentions24h());
        }
        if (request.autoTradeOrderQuoteAmount() != null) {
            row.setAutoTradeOrderQuoteAmount(request.autoTradeOrderQuoteAmount());
        }
        if (request.autoTradeMaxTradesPerDay() != null) {
            row.setAutoTradeMaxTradesPerDay(request.autoTradeMaxTradesPerDay());
        }
        if (request.autoTradeMaxDailyNotional() != null) {
            row.setAutoTradeMaxDailyNotional(request.autoTradeMaxDailyNotional());
        }
        if (request.autoTradeCooldownMinutes() != null) {
            row.setAutoTradeCooldownMinutes(request.autoTradeCooldownMinutes());
        }
        if (request.allowedSymbols() != null) {
            row.setAllowedSymbolsJson(serializeAllowedSymbols(request.allowedSymbols()));
        }
    }

    private RobinhoodRhCryptoAutoTradeSettingsDto toSettingsDto(
            RobinhoodCryptoTradingSettings row, boolean connected) {
        return new RobinhoodRhCryptoAutoTradeSettingsDto(
                autoTradeProps.enabled(),
                connected,
                row.isAutoTradeEnabled(),
                row.isAutoTradeKillSwitch(),
                row.getAutoTradeMinPositivityBuy(),
                row.getAutoTradeMaxPositivitySell(),
                row.getAutoTradeMinSpikeZ(),
                row.getAutoTradeMinMentions24h(),
                row.getAutoTradeOrderQuoteAmount(),
                row.getAutoTradeMaxTradesPerDay(),
                row.getAutoTradeMaxDailyNotional(),
                row.getAutoTradeCooldownMinutes(),
                parseAllowedSymbols(row.getAllowedSymbolsJson()),
                row.getAutoTradeLastRunAt(),
                row.getAutoTradeLastRunMessage() == null ? "" : row.getAutoTradeLastRunMessage());
    }

    RobinhoodRhCryptoOrderDto toOrderDto(RobinhoodCryptoOrder order) {
        return new RobinhoodRhCryptoOrderDto(
                order.getId(),
                order.getStatus(),
                order.getSymbol(),
                order.getTradingPair(),
                order.getSide(),
                order.getOrderType(),
                order.getQuoteAmount(),
                order.getAssetQuantity(),
                order.getEstimatedNotional(),
                order.getSource() == null ? SOURCE_MANUAL : order.getSource(),
                order.getRobinhoodOrderId(),
                order.getErrorMessage(),
                order.getCreatedAt(),
                order.getPlacedAt());
    }

    private void requireSidecar() {
        if (!agenticProps.serviceConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Robinhood sidecar is not configured on this server");
        }
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
