package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoAutoTradeProperties;
import com.svp.tracker.finance.domain.RobinhoodCryptoAutoTradeRun;
import com.svp.tracker.finance.domain.RobinhoodCryptoOrder;
import com.svp.tracker.finance.domain.RobinhoodCryptoTradingSettings;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeEvaluateDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeRunDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeSignalDto;
import com.svp.tracker.finance.predicts.domain.PredictsTicker;
import com.svp.tracker.finance.predicts.dto.PredictsSymbolSummaryDto;
import com.svp.tracker.finance.predicts.repository.PredictsTickerRepository;
import com.svp.tracker.finance.predicts.service.PredictsService;
import com.svp.tracker.finance.repository.RobinhoodCryptoAutoTradeRunRepository;
import com.svp.tracker.finance.repository.RobinhoodCryptoOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodCryptoTradingSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
public class RobinhoodRhCryptoAutoTradeService {

    private static final String SOURCE_AUTO = RobinhoodRhCryptoOrderService.SOURCE_AUTO;

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhCryptoAutoTradeProperties autoTradeProps;
    private final CurrentUserService currentUser;
    private final RobinhoodCryptoTradingService cryptoTradingService;
    private final RobinhoodCryptoTradingSettingsRepository settingsRepository;
    private final RobinhoodCryptoOrderRepository orderRepository;
    private final RobinhoodCryptoAutoTradeRunRepository runRepository;
    private final RobinhoodRhCryptoOrderService orderService;
    private final PredictsService predictsService;
    private final PredictsTickerRepository tickerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public RobinhoodRhCryptoAutoTradeEvaluateDto evaluateForCurrentUser() {
        long uid = currentUser.requireUserId();
        return evaluateForUser(uid, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RobinhoodRhCryptoAutoTradeEvaluateDto evaluateForUser(long ownerUserId, boolean scheduled) {
        requireAutoTradeAllowed();
        RobinhoodCryptoTradingSettings settings = settingsRepository
                .findByOwnerUserId(ownerUserId)
                .orElseGet(() -> {
                    RobinhoodCryptoTradingSettings s = orderService.defaultSettingsTemplate();
                    s.setOwnerUserId(ownerUserId);
                    return s;
                });
        if (!settings.isAutoTradeEnabled()) {
            return skipped("Auto-trade disabled for user");
        }
        if (settings.isAutoTradeKillSwitch()) {
            return skipped("Kill switch active — auto-trade paused");
        }
        if (!cryptoTradingService.isConnected(ownerUserId)) {
            return skipped("Robinhood Crypto Trading API not connected");
        }

        RobinhoodCryptoAutoTradeRun run = new RobinhoodCryptoAutoTradeRun();
        run.setOwnerUserId(ownerUserId);
        run.setStartedAt(Instant.now());
        run.setStatus("running");
        runRepository.save(run);

        List<RobinhoodRhCryptoAutoTradeSignalDto> signals = new ArrayList<>();
        int attempted = 0;
        int placed = 0;
        int generated = 0;
        int evaluated = 0;
        StringBuilder msg = new StringBuilder();

        try {
            if (!withinDailyTradeBudget(ownerUserId, settings)) {
                finishRun(run, settings, ownerUserId, "skipped", evaluated, generated, attempted, placed, "Daily trade limit reached");
                return buildResult(true, "Daily trade limit reached", evaluated, generated, attempted, placed, signals);
            }

            try {
                cryptoTradingService.syncForOwner(ownerUserId);
            } catch (Exception e) {
                if (scheduled && RobinhoodAgenticSidecarErrors.isUnreachable(e)) {
                    String skipReason =
                            "Robinhood sidecar unreachable — ensure robinhood-agent container is running";
                    finishRun(run, settings, ownerUserId, "skipped", evaluated, generated, attempted, placed, skipReason);
                    return skipped(skipReason);
                }
                log.warn("Crypto sync before auto-trade failed for user {}: {}", ownerUserId, e.getMessage());
            }

            Map<String, BigDecimal> heldQty = heldQuantities(ownerUserId);
            List<PredictsTicker> tickers = tickerRepository.findByOwnerUserIdOrderByAutoSeededAscSymbolAsc(ownerUserId);

            for (PredictsTicker ticker : tickers) {
                String symbol = ticker.getSymbol().trim().toUpperCase(Locale.ROOT);
                Optional<String> pairOpt = RobinhoodRhCryptoSymbolMap.toTradingPair(symbol);
                if (pairOpt.isEmpty()) {
                    continue;
                }
                evaluated++;
                String tradingPair = pairOpt.get();
                String asset = RobinhoodRhCryptoSymbolMap.toAssetCode(symbol);

                Optional<SignalDecision> decision = evaluateSymbol(asset, settings, heldQty);
                if (decision.isEmpty()) {
                    continue;
                }
                SignalDecision sig = decision.get();
                generated++;
                signals.add(new RobinhoodRhCryptoAutoTradeSignalDto(
                        asset,
                        tradingPair,
                        sig.side(),
                        sig.reason(),
                        sig.positivityPct(),
                        sig.spikeZ(),
                        sig.mentions24h(),
                        false,
                        ""));

                if (!passesCooldown(ownerUserId, asset, settings)) {
                    updateLastSignal(signals, asset, false, "Cooldown active");
                    continue;
                }

                if (!orderService.isSymbolAllowed(asset, settings)) {
                    updateLastSignal(signals, asset, false, "Not in allowed_symbols whitelist");
                    msg.append(asset).append(" skipped (whitelist); ");
                    continue;
                }

                String signalJson = writeSignalJson(sig);
                attempted++;
                try {
                    BigDecimal quoteAmount = null;
                    BigDecimal assetQuantity = null;
                    if ("buy".equals(sig.side())) {
                        quoteAmount = settings.getAutoTradeOrderQuoteAmount();
                    } else {
                        assetQuantity = heldQty.getOrDefault(asset, BigDecimal.ZERO);
                        if (assetQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                            updateLastSignal(signals, asset, false, "No holdings to sell");
                            attempted--;
                            continue;
                        }
                    }

                    RobinhoodCryptoOrder order = orderService.placeAutoMarketOrder(
                            ownerUserId, asset, tradingPair, sig.side(), quoteAmount, assetQuantity, signalJson);
                    boolean isPlaced = isSuccessfulOrderStatus(order.getStatus());
                    if (isPlaced) {
                        placed++;
                    }
                    updateLastSignal(
                            signals,
                            asset,
                            true,
                            order.getStatus()
                                    + (order.getErrorMessage() != null ? ": " + order.getErrorMessage() : ""));
                    msg.append(asset)
                            .append(' ')
                            .append(sig.side())
                            .append(" → ")
                            .append(order.getStatus())
                            .append("; ");
                } catch (Exception e) {
                    log.warn("Crypto auto-trade order failed for user {} {}: {}", ownerUserId, asset, e.getMessage());
                    updateLastSignal(signals, asset, false, truncate(e.getMessage(), 200));
                    msg.append(asset).append(" error; ");
                }
            }

            String summary = msg.isEmpty() ? "No actionable signals" : msg.toString().trim();
            finishRun(run, settings, ownerUserId, "ok", evaluated, generated, attempted, placed, summary);
            return buildResult(true, summary, evaluated, generated, attempted, placed, signals);
        } catch (Exception e) {
            if (scheduled && RobinhoodAgenticSidecarErrors.isUnreachable(e)) {
                String skipReason =
                        "Robinhood sidecar unreachable — ensure robinhood-agent container is running";
                finishRun(run, settings, ownerUserId, "skipped", evaluated, generated, attempted, placed, skipReason);
                return skipped(skipReason);
            }
            log.error("Crypto auto-trade run failed for user {}", ownerUserId, e);
            String err = truncate(e.getMessage(), 500);
            finishRun(run, settings, ownerUserId, "error", evaluated, generated, attempted, placed, err);
            return buildResult(false, err, evaluated, generated, attempted, placed, signals);
        }
    }

    @Transactional(readOnly = true)
    public List<RobinhoodRhCryptoAutoTradeRunDto> recentRuns() {
        long uid = currentUser.requireUserId();
        return runRepository.findTop20ByOwnerUserIdOrderByStartedAtDesc(uid).stream()
                .map(this::toRunDto)
                .toList();
    }

    public void evaluateAllScheduled() {
        if (!agenticProps.serviceConfigured() || !autoTradeProps.enabled()) {
            return;
        }
        List<RobinhoodCryptoTradingSettings> targets =
                settingsRepository.findByAutoTradeEnabledTrueAndAutoTradeKillSwitchFalse();
        for (RobinhoodCryptoTradingSettings settings : targets) {
            if (!cryptoTradingService.isConnected(settings.getOwnerUserId())) {
                continue;
            }
            try {
                evaluateForUser(settings.getOwnerUserId(), true);
            } catch (Exception e) {
                log.warn(
                        "Scheduled crypto auto-trade failed for user {}: {}",
                        settings.getOwnerUserId(),
                        e.getMessage());
            }
        }
    }

    private Optional<SignalDecision> evaluateSymbol(
            String asset, RobinhoodCryptoTradingSettings settings, Map<String, BigDecimal> heldQty) {
        PredictsSymbolSummaryDto summary = predictsService.summary(asset);
        BigDecimal posPct = summary.overallPositivityPct() == null ? BigDecimal.ZERO : summary.overallPositivityPct();
        BigDecimal spikeZ = summary.overallSpikeZ() == null ? BigDecimal.ZERO : summary.overallSpikeZ();
        int mentions = summary.mentions24h();

        if (mentions < settings.getAutoTradeMinMentions24h()) {
            return Optional.empty();
        }

        BigDecimal held = heldQty.getOrDefault(asset, BigDecimal.ZERO);
        boolean hasPosition = held.compareTo(BigDecimal.ZERO) > 0;

        if (!hasPosition
                && posPct.compareTo(settings.getAutoTradeMinPositivityBuy()) >= 0
                && spikeZ.compareTo(settings.getAutoTradeMinSpikeZ()) >= 0) {
            return Optional.of(new SignalDecision(
                    asset,
                    "buy",
                    "Predicts bullish: positivity "
                            + posPct + "%, spikeZ " + spikeZ + ", mentions " + mentions,
                    posPct,
                    spikeZ,
                    mentions));
        }

        if (hasPosition && posPct.compareTo(settings.getAutoTradeMaxPositivitySell()) <= 0) {
            return Optional.of(new SignalDecision(
                    asset,
                    "sell",
                    "Predicts bearish while holding: positivity "
                            + posPct + "%, mentions " + mentions,
                    posPct,
                    spikeZ,
                    mentions));
        }

        return Optional.empty();
    }

    private Map<String, BigDecimal> heldQuantities(long ownerUserId) {
        Map<String, BigDecimal> map = new HashMap<>();
        cryptoTradingService
                .cachedSyncResult(ownerUserId)
                .map(r -> r.holdings())
                .orElse(List.of())
                .forEach(h -> {
                    if (h.symbol() != null && h.quantity() != null && h.quantity().compareTo(BigDecimal.ZERO) > 0) {
                        map.put(h.symbol().toUpperCase(Locale.ROOT), h.quantity());
                    }
                });
        return map;
    }

    private boolean withinDailyTradeBudget(long ownerUserId, RobinhoodCryptoTradingSettings settings) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        long count = orderRepository.countActiveOrdersSince(ownerUserId, SOURCE_AUTO, startOfDay);
        if (count >= settings.getAutoTradeMaxTradesPerDay()) {
            return false;
        }
        BigDecimal cap = settings.getAutoTradeMaxDailyNotional();
        if (cap != null) {
            BigDecimal spent = orderRepository.sumEstimatedNotionalSince(ownerUserId, SOURCE_AUTO, startOfDay);
            if (spent != null && spent.compareTo(cap) >= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean passesCooldown(long ownerUserId, String asset, RobinhoodCryptoTradingSettings settings) {
        return orderRepository
                .findTopByOwnerUserIdAndSymbolAndSourceOrderByCreatedAtDesc(ownerUserId, asset, SOURCE_AUTO)
                .map(last -> last.getCreatedAt()
                        .plus(settings.getAutoTradeCooldownMinutes(), ChronoUnit.MINUTES)
                        .isBefore(Instant.now()))
                .orElse(true);
    }

    static boolean isSuccessfulOrderStatus(String status) {
        if (status == null) {
            return false;
        }
        String s = status.toLowerCase(Locale.ROOT);
        return s.equals("placed")
                || s.equals("submitted")
                || s.equals("open")
                || s.equals("filled")
                || s.equals("partially_filled");
    }

    private void finishRun(
            RobinhoodCryptoAutoTradeRun run,
            RobinhoodCryptoTradingSettings settings,
            long ownerUserId,
            String status,
            int evaluated,
            int generated,
            int attempted,
            int placed,
            String message) {
        run.setStatus(status);
        run.setTickersEvaluated(evaluated);
        run.setSignalsGenerated(generated);
        run.setOrdersAttempted(attempted);
        run.setOrdersPlaced(placed);
        run.setMessage(truncate(message, 500));
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        RobinhoodCryptoTradingSettings row =
                settingsRepository.findByOwnerUserId(ownerUserId).orElse(settings);
        row.setAutoTradeLastRunAt(run.getFinishedAt());
        row.setAutoTradeLastRunMessage(truncate(message, 500));
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
    }

    private RobinhoodRhCryptoAutoTradeEvaluateDto buildResult(
            boolean ran,
            String message,
            int evaluated,
            int generated,
            int attempted,
            int placed,
            List<RobinhoodRhCryptoAutoTradeSignalDto> signals) {
        return new RobinhoodRhCryptoAutoTradeEvaluateDto(
                ran, message, evaluated, generated, attempted, placed, signals, Instant.now());
    }

    private RobinhoodRhCryptoAutoTradeEvaluateDto skipped(String reason) {
        return new RobinhoodRhCryptoAutoTradeEvaluateDto(false, reason, 0, 0, 0, 0, List.of(), Instant.now());
    }

    private void requireAutoTradeAllowed() {
        if (!agenticProps.serviceConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Robinhood sidecar is not configured");
        }
        if (!autoTradeProps.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Crypto auto-trade is disabled on the server (TRACKER_FINANCE_RH_CRYPTO_AUTO_TRADE_ENABLED)");
        }
    }

    private String writeSignalJson(SignalDecision sig) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("symbol", sig.symbol());
            node.put("side", sig.side());
            node.put("reason", sig.reason());
            node.put("overallPositivityPct", sig.positivityPct());
            node.put("overallSpikeZ", sig.spikeZ());
            node.put("mentions24h", sig.mentions24h());
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"reason\":\"signal\"}";
        }
    }

    private static void updateLastSignal(
            List<RobinhoodRhCryptoAutoTradeSignalDto> signals, String symbol, boolean acted, String result) {
        for (int i = signals.size() - 1; i >= 0; i--) {
            RobinhoodRhCryptoAutoTradeSignalDto s = signals.get(i);
            if (symbol.equals(s.symbol())) {
                signals.set(
                        i,
                        new RobinhoodRhCryptoAutoTradeSignalDto(
                                s.symbol(),
                                s.tradingPair(),
                                s.side(),
                                s.reason(),
                                s.overallPositivityPct(),
                                s.overallSpikeZ(),
                                s.mentions24h(),
                                acted,
                                result));
                break;
            }
        }
    }

    private RobinhoodRhCryptoAutoTradeRunDto toRunDto(RobinhoodCryptoAutoTradeRun run) {
        return new RobinhoodRhCryptoAutoTradeRunDto(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.getTickersEvaluated(),
                run.getSignalsGenerated(),
                run.getOrdersAttempted(),
                run.getOrdersPlaced(),
                run.getMessage());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record SignalDecision(
            String symbol,
            String side,
            String reason,
            BigDecimal positivityPct,
            BigDecimal spikeZ,
            int mentions24h) {}
}
