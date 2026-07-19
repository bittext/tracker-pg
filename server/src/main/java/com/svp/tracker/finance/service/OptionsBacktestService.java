package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.OptionsBacktestEquityPointDto;
import com.svp.tracker.finance.dto.OptionsBacktestRequestDto;
import com.svp.tracker.finance.dto.OptionsBacktestResultDto;
import com.svp.tracker.finance.dto.OptionsBacktestTradeDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Long-call backtest: repeatedly buy one call, hold to expiry, settle intrinsic, then roll.
 *
 * <p>Uses Yahoo daily underlying closes. Option premiums are Black–Scholes estimates with realized
 * volatility from a trailing window — not exchange option quotes. Not investment advice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptionsBacktestService {

    public static final String STRATEGY_ID = "LONG_CALL";
    public static final String STRATEGY_NAME = "Long call";

    private static final String NOTES =
            "Buys one call each cycle, holds to expiry, settles intrinsic value (max(spot − strike, 0)), then rolls. "
                    + "Premiums use Black–Scholes with trailing realized volatility as IV (not live option chains). "
                    + "Assumes 100-share contracts, European-style expiry settlement, no dividends, fees, or early exercise. "
                    + "Educational only — not investment advice.";

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final String YAHOO_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s"
                    + "?period1=%d&period2=%d&interval=1d&includeAdjustedClose=true";

    private static final int DEFAULT_LOOKBACK = 252;
    private static final double DEFAULT_CAPITAL = 100_000.0;
    private static final double DEFAULT_CALL_OTM = 5.0;
    private static final int DEFAULT_DTE = 30;
    private static final double DEFAULT_RF = 0.04;
    private static final int VOL_WINDOW = 21;
    private static final int CONTRACT_MULTIPLIER = 100;

    private final FinanceProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OptionsBacktestResultDto run(OptionsBacktestRequestDto request) {
        String symbol = normalizeSymbol(request == null ? null : request.symbol());
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        int lookback = clamp(request == null ? null : request.lookbackDays(), 60, 1000, DEFAULT_LOOKBACK);
        double capital = clampDouble(request == null ? null : request.startingCapital(), 5_000, 10_000_000, DEFAULT_CAPITAL);
        double callOtm = clampDouble(request == null ? null : request.callOtmPercent(), 0, 40, DEFAULT_CALL_OTM) / 100.0;
        int dte = clamp(request == null ? null : request.daysToExpiration(), 7, 90, DEFAULT_DTE);
        double rf = clampDouble(request == null ? null : request.riskFreeRate(), 0, 0.15, DEFAULT_RF);

        List<Bar> bars = fetchDailyBars(symbol, lookback + VOL_WINDOW + 5);
        if (bars.size() < VOL_WINDOW + dte + 5) {
            throw new IllegalStateException("Not enough price history for " + symbol + " (need ~"
                    + (VOL_WINDOW + dte + 5) + " sessions, got " + bars.size() + ")");
        }

        return simulateLongCalls(symbol, bars, capital, callOtm, dte, rf);
    }

    OptionsBacktestResultDto simulateLongCalls(
            String symbol,
            List<Bar> bars,
            double startingCapital,
            double callOtm,
            int dte,
            double riskFreeRate) {
        double cash = startingCapital;
        int i = VOL_WINDOW;
        List<OptionsBacktestTradeDto> trades = new ArrayList<>();
        List<OptionsBacktestEquityPointDto> curve = new ArrayList<>();
        double peak = startingCapital;
        double maxDrawdown = 0;
        double premiumPaid = 0;
        int wins = 0;
        int losses = 0;

        while (i < bars.size() - 1) {
            int openIdx = i;
            int closeIdx = findExpiryIndex(bars, openIdx, dte);
            if (closeIdx <= openIdx) {
                break;
            }
            Bar open = bars.get(openIdx);
            Bar close = bars.get(closeIdx);
            double sigma = realizedVol(bars, openIdx);
            if (sigma <= 0.01) {
                sigma = 0.20;
            }
            double tYears = Math.max(dte / 365.0, 1.0 / 365.0);
            double strike = roundStrike(open.close * (1.0 + callOtm));
            double premium = blackScholesCall(open.close, strike, tYears, riskFreeRate, sigma);
            double debit = premium * CONTRACT_MULTIPLIER;

            if (cash < debit) {
                curve.add(new OptionsBacktestEquityPointDto(open.date, money(cash)));
                break;
            }

            cash -= debit;
            premiumPaid += debit;

            double intrinsicPerShare = Math.max(close.close - strike, 0);
            double settlement = intrinsicPerShare * CONTRACT_MULTIPLIER;
            cash += settlement;
            double pnl = settlement - debit;
            String outcome;
            if (intrinsicPerShare > 0) {
                outcome = "Expired ITM — settled for intrinsic";
            } else {
                outcome = "Expired OTM — premium lost";
            }
            if (pnl >= 0) {
                wins++;
            } else {
                losses++;
            }

            peak = Math.max(peak, cash);
            maxDrawdown = Math.max(maxDrawdown, peak > 0 ? (peak - cash) / peak : 0);
            curve.add(new OptionsBacktestEquityPointDto(close.date, money(cash)));
            trades.add(trade(
                    open.date,
                    close.date,
                    "BUY_CALL",
                    "CALL",
                    strike,
                    open.close,
                    close.close,
                    premium,
                    pnl,
                    outcome,
                    cash));
            i = closeIdx + 1;
        }

        double ending = cash;
        double retPct = startingCapital == 0 ? 0 : (ending - startingCapital) / startingCapital * 100.0;
        int tradeCount = trades.size();
        double winRate = tradeCount == 0 ? 0 : wins * 100.0 / tradeCount;

        return new OptionsBacktestResultDto(
                STRATEGY_ID,
                STRATEGY_NAME,
                symbol,
                NOTES,
                money(startingCapital),
                money(ending),
                pct(retPct),
                pct(maxDrawdown * 100.0),
                tradeCount,
                wins,
                losses,
                pct(winRate),
                money(premiumPaid),
                List.copyOf(curve),
                List.copyOf(trades));
    }

    private static OptionsBacktestTradeDto trade(
            LocalDate openDate,
            LocalDate closeDate,
            String action,
            String optionType,
            double strike,
            double uOpen,
            double uClose,
            double premium,
            double pnl,
            String outcome,
            double equity) {
        return new OptionsBacktestTradeDto(
                openDate,
                closeDate,
                action,
                optionType,
                money(strike),
                money(uOpen),
                money(uClose),
                money(premium),
                money(pnl),
                outcome,
                money(equity));
    }

    private static int findExpiryIndex(List<Bar> bars, int openIdx, int dte) {
        LocalDate target = bars.get(openIdx).date.plusDays(dte);
        int idx = openIdx;
        for (int j = openIdx; j < bars.size(); j++) {
            idx = j;
            if (!bars.get(j).date.isBefore(target)) {
                return j;
            }
        }
        return idx;
    }

    static double realizedVol(List<Bar> bars, int endIdxExclusive) {
        int start = Math.max(1, endIdxExclusive - VOL_WINDOW);
        List<Double> rets = new ArrayList<>();
        for (int i = start; i < endIdxExclusive; i++) {
            double prev = bars.get(i - 1).close;
            double cur = bars.get(i).close;
            if (prev > 0 && cur > 0) {
                rets.add(Math.log(cur / prev));
            }
        }
        if (rets.size() < 5) {
            return 0.25;
        }
        double mean = 0;
        for (double r : rets) {
            mean += r;
        }
        mean /= rets.size();
        double var = 0;
        for (double r : rets) {
            double d = r - mean;
            var += d * d;
        }
        var /= (rets.size() - 1);
        return Math.sqrt(var * 252.0);
    }

    /** Black–Scholes call price. */
    static double blackScholesCall(double spot, double strike, double t, double r, double sigma) {
        if (t <= 0 || sigma <= 0 || spot <= 0 || strike <= 0) {
            return Math.max(spot - strike, 0);
        }
        double d1 = (Math.log(spot / strike) + (r + 0.5 * sigma * sigma) * t) / (sigma * Math.sqrt(t));
        double d2 = d1 - sigma * Math.sqrt(t);
        return spot * normCdf(d1) - strike * Math.exp(-r * t) * normCdf(d2);
    }

    /** Black–Scholes put price (kept for put-call parity tests). */
    static double blackScholesPut(double spot, double strike, double t, double r, double sigma) {
        if (t <= 0 || sigma <= 0 || spot <= 0 || strike <= 0) {
            return Math.max(strike - spot, 0);
        }
        double d1 = (Math.log(spot / strike) + (r + 0.5 * sigma * sigma) * t) / (sigma * Math.sqrt(t));
        double d2 = d1 - sigma * Math.sqrt(t);
        return strike * Math.exp(-r * t) * normCdf(-d2) - spot * normCdf(-d1);
    }

    static double normCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    /** Abramowitz & Stegun error-function approximation. */
    static double erf(double z) {
        double sign = z < 0 ? -1 : 1;
        double x = Math.abs(z);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double y =
                1.0
                        - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
                                        + 0.254829592)
                                * t
                                * Math.exp(-x * x);
        return sign * y;
    }

    private List<Bar> fetchDailyBars(String symbol, int sessionsNeeded) throws IllegalStateException {
        try {
            LocalDate end = LocalDate.now(NY).plusDays(1);
            LocalDate start = end.minusDays((long) (sessionsNeeded * 1.7) + 40);
            long period1 = start.atStartOfDay(NY).toEpochSecond();
            long period2 = end.atStartOfDay(NY).toEpochSecond();
            String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
            String url = String.format(Locale.ROOT, CHART_URL, enc, period1, period2);
            int timeoutMs = Math.max(props.newsTimeoutMs(), 20_000);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .header("User-Agent", YAHOO_USER_AGENT)
                    .build();
            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("Yahoo chart HTTP " + resp.statusCode());
            }
            return parseBars(objectMapper.readTree(resp.body()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("options backtest price fetch failed for {}", symbol, e);
            throw new IllegalStateException("Could not load price history for " + symbol + ": " + e.getMessage(), e);
        }
    }

    static List<Bar> parseBars(JsonNode root) {
        List<Bar> out = new ArrayList<>();
        JsonNode results = root.path("chart").path("result");
        if (!results.isArray() || results.isEmpty()) {
            return out;
        }
        JsonNode r0 = results.get(0);
        JsonNode ts = r0.path("timestamp");
        JsonNode adj = r0.path("indicators").path("adjclose").path(0).path("adjclose");
        JsonNode raw = r0.path("indicators").path("quote").path(0).path("close");
        for (int i = 0; i < ts.size(); i++) {
            JsonNode t = ts.get(i);
            JsonNode p = i < adj.size() ? adj.get(i) : null;
            if (p == null || !p.isNumber()) {
                p = i < raw.size() ? raw.get(i) : null;
            }
            if (t == null || !t.isNumber() || p == null || !p.isNumber()) {
                continue;
            }
            double px = p.asDouble();
            if (!Double.isFinite(px) || px <= 0) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(t.asLong()).atZone(NY).toLocalDate();
            out.add(new Bar(date, px));
        }
        return out;
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9.\\-^=]", "");
    }

    private static int clamp(Integer v, int min, int max, int def) {
        if (v == null) {
            return def;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static double clampDouble(Double v, double min, double max, double def) {
        if (v == null || !Double.isFinite(v)) {
            return def;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static double roundStrike(double raw) {
        if (raw >= 100) {
            return Math.round(raw);
        }
        if (raw >= 50) {
            return Math.round(raw * 2) / 2.0;
        }
        return Math.round(raw * 4) / 4.0;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    record Bar(LocalDate date, double close) {}
}
