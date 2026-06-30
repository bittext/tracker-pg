package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticAutoTradeRun;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticOrder;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.domain.RobinhoodAgenticSettings;
import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeEvaluateDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeRunDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeSignalDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderRequestDto;
import com.svp.tracker.finance.predicts.domain.PredictsTicker;
import com.svp.tracker.finance.predicts.dto.PredictsSymbolSummaryDto;
import com.svp.tracker.finance.predicts.repository.PredictsTickerRepository;
import com.svp.tracker.finance.predicts.service.PredictsService;
import com.svp.tracker.finance.repository.RobinhoodAgenticAutoTradeRunRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticPositionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSettingsRepository;
import com.svp.tracker.auth.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
public class RobinhoodAgenticAutoTradeService {

    private static final String SOURCE_AUTO = "auto";
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticAutoTradeProperties autoTradeProps;
    private final CurrentUserService currentUser;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticSettingsRepository settingsRepository;
    private final RobinhoodAgenticOrderRepository orderRepository;
    private final RobinhoodAgenticPositionRepository positionRepository;
    private final RobinhoodAgenticAutoTradeRunRepository runRepository;
    private final RobinhoodAgenticOrderService orderService;
    private final RobinhoodAgenticAdminDefaultsService adminDefaultsService;
    private final RobinhoodAgenticService agenticService;
    private final PredictsService predictsService;
    private final PredictsTickerRepository tickerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public RobinhoodAgenticAutoTradeEvaluateDto evaluateForCurrentUser() {
        long uid = currentUser.requireUserId();
        return evaluateForUser(uid, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RobinhoodAgenticAutoTradeEvaluateDto evaluateForUser(long ownerUserId, boolean scheduled) {
        requireAutoTradeAllowed();
        RobinhoodAgenticSettings settings = settingsRepository
                .findByOwnerUserId(ownerUserId)
                .orElseGet(adminDefaultsService::newUserSettingsTemplate);
        if (!settings.isAutoTradeEnabled()) {
            return skipped("Auto-trade disabled for user");
        }
        if (settings.isAutoTradeKillSwitch()) {
            return skipped("Kill switch active — auto-trade paused");
        }
        if (!connectionRepository.findByOwnerUserId(ownerUserId).isPresent()) {
            return skipped("Robinhood Agentic not connected");
        }

        RobinhoodAgenticAutoTradeRun run = new RobinhoodAgenticAutoTradeRun();
        run.setOwnerUserId(ownerUserId);
        run.setStartedAt(Instant.now());
        run.setStatus("running");
        runRepository.save(run);

        List<RobinhoodAgenticAutoTradeSignalDto> signals = new ArrayList<>();
        int reviewed = 0;
        int placed = 0;
        int generated = 0;
        int evaluated = 0;
        StringBuilder msg = new StringBuilder();

        try {
            if (settings.isAutoTradeMarketHoursOnly() && !isUsMarketOpen()) {
                finishRun(run, settings, ownerUserId, "skipped", evaluated, generated, reviewed, placed, "Outside US market hours");
                return buildResult(true, "Outside US market hours", evaluated, generated, reviewed, placed, signals);
            }

            if (!withinDailyTradeBudget(ownerUserId, settings)) {
                finishRun(run, settings, ownerUserId, "skipped", evaluated, generated, reviewed, placed, "Daily trade limit reached");
                return buildResult(true, "Daily trade limit reached", evaluated, generated, reviewed, placed, signals);
            }

            if (!agenticService.syncConnectionBestEffort(
                    connectionRepository.findByOwnerUserId(ownerUserId).orElseThrow())) {
                String skipReason =
                        "Robinhood Agentic sidecar unreachable — ensure robinhood-agent container is running";
                finishRun(run, settings, ownerUserId, "skipped", evaluated, generated, reviewed, placed, skipReason);
                return skipped(skipReason);
            }
            Map<String, BigDecimal> heldQty = heldQuantities(ownerUserId);
            List<PredictsTicker> tickers = tickerRepository.findByOwnerUserIdOrderByAutoSeededAscSymbolAsc(ownerUserId);
            evaluated = tickers.size();

            for (PredictsTicker ticker : tickers) {
                String symbol = ticker.getSymbol().trim().toUpperCase(Locale.ROOT);
                Optional<SignalDecision> decision = evaluateSymbol(symbol, settings, heldQty);
                if (decision.isEmpty()) {
                    continue;
                }
                SignalDecision sig = decision.get();
                generated++;
                signals.add(new RobinhoodAgenticAutoTradeSignalDto(
                        symbol,
                        sig.side(),
                        sig.reason(),
                        sig.positivityPct(),
                        sig.spikeZ(),
                        sig.mentions24h(),
                        false,
                        ""));

                if (!passesCooldown(ownerUserId, symbol, settings)) {
                    updateLastSignal(signals, symbol, false, "Cooldown active");
                    continue;
                }

                if (!orderService.isSymbolAllowed(symbol, settings)) {
                    updateLastSignal(signals, symbol, false, "Not in allowed_symbols whitelist");
                    msg.append(symbol).append(" skipped (whitelist); ");
                    continue;
                }

                String signalJson = writeSignalJson(sig);
                RobinhoodAgenticOrderRequestDto request = new RobinhoodAgenticOrderRequestDto(
                        symbol, sig.side(), "market", settings.getAutoTradeOrderQuantity(), null, null, null);
                try {
                    RobinhoodAgenticOrder order = orderService.reviewOrderForUser(
                            ownerUserId, request, SOURCE_AUTO, signalJson, true);
                    reviewed++;
                    boolean isPlaced = "placed".equals(order.getStatus());
                    if (isPlaced) {
                        placed++;
                    }
                    updateLastSignal(
                            signals,
                            symbol,
                            true,
                            order.getStatus() + (order.getErrorMessage() != null ? ": " + order.getErrorMessage() : ""));
                    msg.append(symbol)
                            .append(' ')
                            .append(sig.side())
                            .append(" → ")
                            .append(order.getStatus())
                            .append("; ");
                } catch (Exception e) {
                    log.warn("Auto-trade order failed for user {} {}: {}", ownerUserId, symbol, e.getMessage());
                    updateLastSignal(signals, symbol, false, truncate(e.getMessage(), 200));
                    msg.append(symbol).append(" error; ");
                }
            }

            String summary = msg.isEmpty() ? "No actionable signals" : msg.toString().trim();
            finishRun(run, settings, ownerUserId, "ok", evaluated, generated, reviewed, placed, summary);
            return buildResult(true, summary, evaluated, generated, reviewed, placed, signals);
        } catch (Exception e) {
            if (scheduled && (RobinhoodAgenticSidecarErrors.isUnreachable(e)
                    || (e instanceof ResponseStatusException rex && RobinhoodAgenticSidecarErrors.isSidecarDown(rex)))) {
                String skipReason =
                        "Robinhood Agentic sidecar unreachable — ensure robinhood-agent container is running";
                log.warn("Auto-trade skipped for user {}: {}", ownerUserId, rootCauseMessage(e));
                finishRun(run, settings, ownerUserId, "skipped", evaluated, generated, reviewed, placed, skipReason);
                return skipped(skipReason);
            }
            log.error("Auto-trade run failed for user {}", ownerUserId, e);
            String err = truncate(e.getMessage(), 500);
            finishRun(run, settings, ownerUserId, "error", evaluated, generated, reviewed, placed, err);
            return buildResult(false, err, evaluated, generated, reviewed, placed, signals);
        }
    }

    @Transactional(readOnly = true)
    public List<RobinhoodAgenticAutoTradeRunDto> recentRuns() {
        long uid = currentUser.requireUserId();
        return runRepository.findTop20ByOwnerUserIdOrderByStartedAtDesc(uid).stream()
                .map(this::toRunDto)
                .toList();
    }

    /** Scheduled entry: all users with auto-trade enabled and kill switch off. */
    public void evaluateAllScheduled() {
        if (!agenticProps.enabled() || !autoTradeProps.enabled() || !agenticProps.executionEnabled()) {
            return;
        }
        List<RobinhoodAgenticSettings> targets = settingsRepository.findByAutoTradeEnabledTrueAndAutoTradeKillSwitchFalse();
        for (RobinhoodAgenticSettings settings : targets) {
            if (!connectionRepository.findByOwnerUserId(settings.getOwnerUserId()).isPresent()) {
                continue;
            }
            try {
                evaluateForUser(settings.getOwnerUserId(), true);
            } catch (Exception e) {
                log.warn("Scheduled auto-trade failed for user {}: {}", settings.getOwnerUserId(), e.getMessage());
            }
        }
    }

    private Optional<SignalDecision> evaluateSymbol(
            String symbol, RobinhoodAgenticSettings settings, Map<String, BigDecimal> heldQty) {
        PredictsSymbolSummaryDto summary = predictsService.summary(symbol);
        BigDecimal posPct = summary.overallPositivityPct() == null ? BigDecimal.ZERO : summary.overallPositivityPct();
        BigDecimal spikeZ = summary.overallSpikeZ() == null ? BigDecimal.ZERO : summary.overallSpikeZ();
        int mentions = summary.mentions24h();

        if (mentions < settings.getAutoTradeMinMentions24h()) {
            return Optional.empty();
        }

        BigDecimal held = heldQty.getOrDefault(symbol, BigDecimal.ZERO);
        boolean hasPosition = held.compareTo(BigDecimal.ZERO) > 0;

        if (!hasPosition
                && posPct.compareTo(settings.getAutoTradeMinPositivityBuy()) >= 0
                && spikeZ.compareTo(settings.getAutoTradeMinSpikeZ()) >= 0) {
            return Optional.of(new SignalDecision(
                    symbol,
                    "buy",
                    "Predicts bullish: positivity "
                            + posPct + "%, spikeZ " + spikeZ + ", mentions " + mentions,
                    posPct,
                    spikeZ,
                    mentions));
        }

        if (hasPosition && posPct.compareTo(settings.getAutoTradeMaxPositivitySell()) <= 0) {
            return Optional.of(new SignalDecision(
                    symbol,
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
        for (RobinhoodAgenticPosition p :
                positionRepository.findByOwnerUserIdOrderByPositionTypeAscSymbolAscChainSymbolAsc(ownerUserId)) {
            if (!"equity".equalsIgnoreCase(p.getPositionType())) {
                continue;
            }
            BigDecimal qty = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
            map.merge(p.getSymbol().toUpperCase(Locale.ROOT), qty, BigDecimal::add);
        }
        return map;
    }

    private boolean withinDailyTradeBudget(long ownerUserId, RobinhoodAgenticSettings settings) {
        Instant startOfDay = LocalDate.now(US_EASTERN).atStartOfDay(US_EASTERN).toInstant();
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

    private boolean passesCooldown(long ownerUserId, String symbol, RobinhoodAgenticSettings settings) {
        return orderRepository
                .findTopByOwnerUserIdAndSymbolAndSourceOrderByCreatedAtDesc(ownerUserId, symbol, SOURCE_AUTO)
                .map(last -> last.getCreatedAt()
                        .plus(settings.getAutoTradeCooldownMinutes(), ChronoUnit.MINUTES)
                        .isBefore(Instant.now()))
                .orElse(true);
    }

    static boolean isUsMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        DayOfWeek dow = now.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime t = now.toLocalTime();
        return !t.isBefore(LocalTime.of(9, 30)) && t.isBefore(LocalTime.of(16, 0));
    }

    private void finishRun(
            RobinhoodAgenticAutoTradeRun run,
            RobinhoodAgenticSettings settings,
            long ownerUserId,
            String status,
            int evaluated,
            int generated,
            int reviewed,
            int placed,
            String message) {
        run.setStatus(status);
        run.setTickersEvaluated(evaluated);
        run.setSignalsGenerated(generated);
        run.setOrdersReviewed(reviewed);
        run.setOrdersPlaced(placed);
        run.setMessage(truncate(message, 500));
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        RobinhoodAgenticSettings row = settingsRepository.findByOwnerUserId(ownerUserId).orElse(settings);
        row.setAutoTradeLastRunAt(run.getFinishedAt());
        row.setAutoTradeLastRunMessage(truncate(message, 500));
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
    }

    private RobinhoodAgenticAutoTradeEvaluateDto buildResult(
            boolean ran,
            String message,
            int evaluated,
            int generated,
            int reviewed,
            int placed,
            List<RobinhoodAgenticAutoTradeSignalDto> signals) {
        return new RobinhoodAgenticAutoTradeEvaluateDto(
                ran, message, evaluated, generated, reviewed, placed, signals, Instant.now());
    }

    private RobinhoodAgenticAutoTradeEvaluateDto skipped(String reason) {
        return new RobinhoodAgenticAutoTradeEvaluateDto(false, reason, 0, 0, 0, 0, List.of(), Instant.now());
    }

    private void requireAutoTradeAllowed() {
        if (!agenticProps.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Robinhood Agentic is disabled");
        }
        if (!autoTradeProps.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "AI auto-trade is disabled on the server (TRACKER_FINANCE_ROBINHOOD_AGENTIC_AUTO_TRADE_ENABLED)");
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
            List<RobinhoodAgenticAutoTradeSignalDto> signals, String symbol, boolean acted, String result) {
        for (int i = signals.size() - 1; i >= 0; i--) {
            RobinhoodAgenticAutoTradeSignalDto s = signals.get(i);
            if (symbol.equals(s.symbol())) {
                signals.set(
                        i,
                        new RobinhoodAgenticAutoTradeSignalDto(
                                s.symbol(),
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

    private RobinhoodAgenticAutoTradeRunDto toRunDto(RobinhoodAgenticAutoTradeRun run) {
        return new RobinhoodAgenticAutoTradeRunDto(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.getTickersEvaluated(),
                run.getSignalsGenerated(),
                run.getOrdersReviewed(),
                run.getOrdersPlaced(),
                run.getMessage());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private record SignalDecision(
            String symbol,
            String side,
            String reason,
            BigDecimal positivityPct,
            BigDecimal spikeZ,
            int mentions24h) {}
}
