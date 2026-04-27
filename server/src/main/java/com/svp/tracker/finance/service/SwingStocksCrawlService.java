package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.SectorPeerMoveDto;
import com.svp.tracker.finance.dto.StockNewsAnalysisDto;
import com.svp.tracker.finance.dto.StockNewsDto;
import com.svp.tracker.finance.dto.StockNewsItemDto;
import com.svp.tracker.finance.dto.StockNewsStressSignalsDto;
import com.svp.tracker.finance.dto.SwingStockDetailDto;
import com.svp.tracker.finance.dto.SwingStocksSectionDto;
import com.svp.tracker.finance.dto.YahooExtendedQuoteDto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Picks the largest |session %| names from Yahoo day gainers/losers (and most actives) and layers extended quotes,
 * headline sentiment, and same-sector screener peers. Narratives are heuristics — not investment advice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwingStocksCrawlService {
    private static final String SCREENER_BASE =
            "https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved"
                    + "?formatted=true&lang=en-US&region=US&count=%d&scrIds=%s";
    private static final int SCREENER_PAGE = 100;
    private static final int TOP_SWINGS = 6;
    private static final int SECTOR_PEERS_EACH = 3;
    private static final int CHUNK = 12;

    private final FinanceProperties props;
    private final StockNewsService stockNewsService;
    private final YahooBatchQuoteService yahooBatchQuoteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SwingStocksSectionDto buildSection(int newsHeadlineLimit) {
        if (!props.newsEnabled()) {
            return new SwingStocksSectionDto(
                    "disabled",
                    "Enable tracker.finance.news-enabled to load swing headlines and narratives.",
                    Instant.now().toString(),
                    0,
                    List.of());
        }
        int newsLimit = Math.max(10, Math.min(30, newsHeadlineLimit));
        String note =
                "Sources: Yahoo Finance screeners (day gainers, losers, most actives) for session movers; Alpha Vantage bulk quote"
                        + " KPIs; Google News RSS (trusted sources) for headlines. Peer rows are other names in the"
                        + " screener mix with the same Yahoo sector when available. “Next few sessions” lines are"
                        + " rule-based heuristics from those inputs — not sell-side consensus or a forecast. Automated,"
                        + " incomplete — not investment advice.";
        String fetched = Instant.now().toString();
        try {
            JsonNode gainers = fetchScreenerJson("day_gainers", SCREENER_PAGE);
            JsonNode losers = fetchScreenerJson("day_losers", SCREENER_PAGE);
            JsonNode actives = fetchScreenerJsonOptional("most_actives", 80);
            Map<String, JsonNode> bySymbol = mergeScreenerQuotesByMaxAbsChg(gainers, losers, actives);
            if (bySymbol.isEmpty()) {
                return new SwingStocksSectionDto("Yahoo screeners", "No screener quotes returned in this run.", fetched, 0, List.of());
            }
            List<String> topSyms = pickTopSymbolsByAbsChg(bySymbol, TOP_SWINGS);
            if (topSyms.isEmpty()) {
                return new SwingStocksSectionDto("Yahoo screeners", "Could not rank swing names.", fetched, 0, List.of());
            }
            Map<String, String> sectorBySymbol = new HashMap<>();
            Map<String, Double> chgBySymbol = new HashMap<>();
            for (Map.Entry<String, JsonNode> e : bySymbol.entrySet()) {
                String sym = e.getKey();
                String sec = screenerText(e.getValue(), "sector");
                if (sec != null && !sec.isBlank()) {
                    sectorBySymbol.put(sym, sec);
                }
                chgBySymbol.put(sym, dbl(e.getValue(), "regularMarketChangePercent") == null
                        ? 0.0
                        : dbl(e.getValue(), "regularMarketChangePercent"));
            }
            Map<String, YahooExtendedQuoteDto> ext = batchExtended(new HashSet<>(topSyms));
            for (String s : topSyms) {
                YahooExtendedQuoteDto e = ext.get(s);
                if (e != null && e.sector() != null && !e.sector().isBlank()) {
                    sectorBySymbol.put(s, e.sector());
                }
            }
            Set<String> moreExt = new HashSet<>();
            for (String t : topSyms) {
                moreExt.addAll(pickSectorPeers(t, sectorBySymbol, chgBySymbol, bySymbol.keySet()));
            }
            moreExt.removeIf(sym -> ext.containsKey(sym));
            if (!moreExt.isEmpty()) {
                ext.putAll(batchExtended(moreExt));
            }

            List<SwingStockDetailDto> rows = new ArrayList<>();
            for (String sym : topSyms) {
                YahooExtendedQuoteDto qe = ext.get(sym);
                boolean batchHadRow = qe != null;
                if (qe == null) {
                    qe = extendedFromScreener(sym, bySymbol.get(sym));
                }
                if (qe == null) {
                    rows.add(missingRow(sym, "No extended quote row"));
                    continue;
                }
                List<String> warns = new ArrayList<>();
                if (!batchHadRow) {
                    warns.add(
                            "Alpha bulk quote omitted this symbol or returned no row; KPIs use the screener quote"
                                    + " where available.");
                }
                StockNewsDto news;
                String company = pickCompanyName(qe);
                try {
                    news = stockNewsService.fetchLatestNews(sym, company, newsLimit);
                } catch (Exception e) {
                    log.debug("News for {} failed: {}", sym, e.getMessage());
                    warns.add("News: " + (e.getMessage() == null ? "n/a" : e.getMessage().replace('\n', ' ')));
                    news = emptyNews(sym, company, newsLimit);
                }
                List<SectorPeerMoveDto> peers = buildPeerDtos(
                        sym, pickSectorPeers(sym, sectorBySymbol, chgBySymbol, bySymbol.keySet()), bySymbol, ext, sectorBySymbol);
                String kpi = buildKpiNarrative(qe);
                String perf = buildPerformanceReport(qe, news, peers);
                NearTermOutlook near = buildNearTermOutlook(qe, news, peers);
                rows.add(new SwingStockDetailDto(
                        qe, news, perf, kpi, peers, List.copyOf(warns), near.tilt(), near.narrative()));
            }
            return new SwingStocksSectionDto("Yahoo + Google News RSS", note, fetched, topSyms.size(), rows);
        } catch (Exception e) {
            log.warn("Swing stocks build failed", e);
            return new SwingStocksSectionDto(
                    "error",
                    "Could not build swing list: " + (e.getMessage() == null ? "unknown" : e.getMessage()),
                    fetched,
                    0,
                    List.of());
        }
    }

    private static String pickCompanyName(YahooExtendedQuoteDto q) {
        if (q.longName() != null && !q.longName().isBlank()) {
            return q.longName();
        }
        if (q.shortName() != null && !q.shortName().isBlank()) {
            return q.shortName();
        }
        return q.symbol();
    }

    /**
     * When v7/quote drops a symbol, reuse the same screener JSON we ranked on so price, %, name, and sector still
     * render.
     */
    private static YahooExtendedQuoteDto extendedFromScreener(String sym, JsonNode q) {
        if (q == null || q.isNull()) {
            return null;
        }
        String shortNm = screenerText(q, "shortName");
        Long vol3m = longFromScreener(q, "averageDailyVolume3Month");
        if (vol3m == null) {
            vol3m = longFromScreener(q, "averageDailyVolume10Day");
        }
        return new YahooExtendedQuoteDto(
                sym,
                shortNm != null && !shortNm.isBlank() ? shortNm : sym,
                screenerText(q, "longName"),
                dbl(q, "regularMarketPrice"),
                dbl(q, "regularMarketChangePercent"),
                longFromScreener(q, "regularMarketVolume"),
                vol3m,
                dbl(q, "marketCap"),
                dbl(q, "fiftyTwoWeekHigh"),
                dbl(q, "fiftyTwoWeekLow"),
                dbl(q, "trailingPE"),
                screenerText(q, "sector"),
                screenerText(q, "industry"));
    }

    private static Long longFromScreener(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (v.isObject() && v.has("raw") && !v.get("raw").isNull()) {
            v = v.get("raw");
        }
        if (v.isIntegralNumber()) {
            return v.asLong();
        }
        if (v.isNumber()) {
            return Math.round(v.asDouble());
        }
        if (v.isTextual()) {
            String s = v.asText();
            if (s != null && !s.isBlank()) {
                try {
                    return Math.round(Double.parseDouble(s.trim().replace(",", "")));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static SwingStockDetailDto missingRow(String sym, String msg) {
        YahooExtendedQuoteDto qe =
                new YahooExtendedQuoteDto(
                        sym, sym, null, null, null, null, null, null, null, null, null, null, null);
        return new SwingStockDetailDto(
                qe,
                emptyNews(sym, sym, 0),
                msg,
                "—",
                List.of(),
                List.of(msg),
                "Unavailable",
                "Near-term persistence read needs price and session context from Yahoo; none was available for this row.");
    }

    private static StockNewsDto emptyNews(String sym, String co, int limit) {
        StockNewsAnalysisDto a =
                new StockNewsAnalysisDto(
                        "Neutral",
                        0.0,
                        50,
                        "Sideways",
                        new StockNewsStressSignalsDto(0, 0, 0, 0, 0, "Low"));
        return new StockNewsDto(
                sym, co, limit, 0, "Google News RSS", Instant.now().toString(), "—", a, List.<StockNewsItemDto>of());
    }

    private Map<String, YahooExtendedQuoteDto> batchExtended(Set<String> syms) {
        List<String> list = new ArrayList<>(syms);
        Collections.sort(list);
        Map<String, YahooExtendedQuoteDto> out = new HashMap<>();
        for (int i = 0; i < list.size(); i += CHUNK) {
            int to = Math.min(i + CHUNK, list.size());
            out.putAll(yahooBatchQuoteService.fetchExtendedBySymbols(list.subList(i, to)));
        }
        return out;
    }

    private static List<String> pickSectorPeers(
            String mainSym,
            Map<String, String> sectorBySymbol,
            Map<String, Double> chgBySymbol,
            Set<String> universe) {
        String sec = sectorBySymbol.get(mainSym);
        if (sec == null || sec.isBlank()) {
            return List.of();
        }
        return universe.stream()
                .filter(s -> !s.equalsIgnoreCase(mainSym))
                .filter(s -> sec.equalsIgnoreCase(sectorBySymbol.get(s)))
                .filter(s -> chgBySymbol.containsKey(s))
                .sorted(Comparator.comparing(
                                (String s) -> Math.abs(chgBySymbol.getOrDefault(s, 0.0)))
                        .reversed())
                .limit(SECTOR_PEERS_EACH)
                .toList();
    }

    private static List<SectorPeerMoveDto> buildPeerDtos(
            String main,
            List<String> peerSyms,
            Map<String, JsonNode> bySymbol,
            Map<String, YahooExtendedQuoteDto> ext,
            Map<String, String> sectorBySymbol) {
        List<SectorPeerMoveDto> out = new ArrayList<>();
        for (String s : peerSyms) {
            if (s.equalsIgnoreCase(main)) {
                continue;
            }
            YahooExtendedQuoteDto qe = ext.get(s);
            JsonNode sc = bySymbol.get(s);
            if (qe != null) {
                out.add(
                        new SectorPeerMoveDto(
                                s,
                                eStr(qe.shortName()),
                                qe.regularMarketChangePercent(),
                                qe.regularMarketPrice(),
                                nz(qe.sector(), sectorBySymbol.get(s))));
            } else if (sc != null) {
                out.add(
                        new SectorPeerMoveDto(
                                s,
                                screenerText(sc, "shortName"),
                                dbl(sc, "regularMarketChangePercent"),
                                dbl(sc, "regularMarketPrice"),
                                screenerText(sc, "sector")));
            }
        }
        return out;
    }

    private static String nz(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private static String eStr(String s) {
        return s == null ? "" : s;
    }

    private static String buildKpiNarrative(YahooExtendedQuoteDto q) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                Locale.US, "Price $%,.2f, session %+.2f%%.", nvl(q.regularMarketPrice(), 0.0), nvl(q.regularMarketChangePercent(), 0.0)));
        if (q.fiftyTwoWeekHigh() != null
                && q.fiftyTwoWeekLow() != null
                && q.fiftyTwoWeekHigh() > q.fiftyTwoWeekLow()
                && q.regularMarketPrice() != null) {
            double pos =
                    100.0
                            * (q.regularMarketPrice() - q.fiftyTwoWeekLow())
                            / (q.fiftyTwoWeekHigh() - q.fiftyTwoWeekLow());
            sb.append(String.format(
                    Locale.US, " ~%.0f%% of the 52-week range (low $%.2f, high $%.2f).",
                    pos, q.fiftyTwoWeekLow(), q.fiftyTwoWeekHigh()));
        } else {
            sb.append(" 52w range: ");
            if (q.fiftyTwoWeekLow() != null && q.fiftyTwoWeekHigh() != null) {
                sb.append(String.format(Locale.US, "low $%.2f, high $%.2f. ", q.fiftyTwoWeekLow(), q.fiftyTwoWeekHigh()));
            } else {
                sb.append("n/a. ");
            }
        }
        if (q.trailingPE() != null) {
            sb.append(String.format(Locale.US, " Trailing P/E ≈ %.1f.", q.trailingPE()));
        } else {
            sb.append(" Trailing P/E not in feed.");
        }
        if (q.marketCap() != null) {
            sb.append(String.format(Locale.US, " Market cap (raw from feed) ≈ %.0f. ", q.marketCap()));
        }
        return sb.toString();
    }

    private static String buildPerformanceReport(
            YahooExtendedQuoteDto q, StockNewsDto news, List<SectorPeerMoveDto> peers) {
        StringBuilder sb = new StringBuilder();
        if (q.regularMarketChangePercent() != null) {
            if (q.regularMarketChangePercent() >= 3) {
                sb.append("Intraday move is large on a percentage basis. ");
            } else if (q.regularMarketChangePercent() <= -3) {
                sb.append("Intraday move is meaningfully negative. ");
            } else {
                sb.append("Intraday move is moderate. ");
            }
        }
        if (q.regularMarketVolume() != null && q.averageDailyVolume3Month() != null && q.averageDailyVolume3Month() > 0) {
            double r = (double) q.regularMarketVolume() / (double) q.averageDailyVolume3Month();
            if (r >= 1.6) {
                sb.append(String.format(
                        Locale.US, "Session volume is elevated (~%.1f× a rough 3‑month average daily print). ", r));
            } else if (r <= 0.65) {
                sb.append("Session volume is light vs recent averages. ");
            } else {
                sb.append("Volume is in a normal band vs the feed’s 3‑month average. ");
            }
        } else {
            sb.append("Volume / average volume ratio not available. ");
        }
        if (news != null && news.analysis() != null) {
            var a = news.analysis();
            sb.append("Headline scan (heuristic): sentiment ")
                    .append(a.overallSentiment())
                    .append(", growth label ")
                    .append(a.projectedGrowthLabel())
                    .append(", deal/permits stress bucket ")
                    .append(a.stressSignals() != null ? a.stressSignals().emphasis() : "n/a")
                    .append(
                            ". This is a keyword‑based read of recent titles — not a fundamental research conclusion. ");
        }
        if (!peers.isEmpty()) {
            sb.append(" Other sector screener moves today: ");
            for (int i = 0; i < peers.size(); i++) {
                SectorPeerMoveDto p = peers.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(
                        String.format(
                                Locale.US, "%s %+.2f%%", p.symbol(), p.regularMarketChangePercent() == null
                                        ? 0.0
                                        : p.regularMarketChangePercent()));
            }
            sb.append(". ");
        } else {
            sb.append("No other same‑sector screener matches with a sector label. ");
        }
        sb.append("Double‑check filings, firm guidance, and the tape.");
        return sb.toString();
    }

    /**
     * Rule-based tilt about whether the session impulse is more likely to extend, fade, or chop over the next few
     * trading days — explicitly not analyst consensus or a model forecast.
     */
    private static NearTermOutlook buildNearTermOutlook(
            YahooExtendedQuoteDto q, StockNewsDto news, List<SectorPeerMoveDto> peers) {
        Double chg = q.regularMarketChangePercent();
        double absChg = chg == null ? 0.0 : Math.abs(chg);
        double score = 0.0;

        if (absChg >= 15) {
            score -= 3.0;
        } else if (absChg >= 10) {
            score -= 2.2;
        } else if (absChg >= 6) {
            score -= 1.4;
        } else if (absChg >= 3) {
            score -= 0.5;
        } else if (absChg > 0 && absChg < 1.0) {
            score += 0.2;
        }

        if (q.regularMarketVolume() != null
                && q.averageDailyVolume3Month() != null
                && q.averageDailyVolume3Month() > 0) {
            double r = (double) q.regularMarketVolume() / (double) q.averageDailyVolume3Month();
            if (r >= 2.2) {
                score += 2.0;
            } else if (r >= 1.5) {
                score += 1.1;
            } else if (r >= 1.15) {
                score += 0.4;
            } else if (r <= 0.5) {
                score -= 0.6;
            }
        }

        String sent =
                news != null && news.analysis() != null && news.analysis().overallSentiment() != null
                        ? news.analysis().overallSentiment().trim()
                        : "Neutral";
        boolean posSent = sent.equalsIgnoreCase("Positive");
        boolean negSent = sent.equalsIgnoreCase("Negative");
        if (chg != null) {
            if (chg > 0.35) {
                if (posSent) {
                    score += 1.0;
                } else if (negSent) {
                    score -= 1.3;
                }
            } else if (chg < -0.35) {
                if (negSent) {
                    score += 0.9;
                } else if (posSent) {
                    score -= 0.9;
                }
            }
        }

        if (q.regularMarketPrice() != null
                && q.fiftyTwoWeekHigh() != null
                && q.fiftyTwoWeekLow() != null) {
            double hi = q.fiftyTwoWeekHigh();
            double lo = q.fiftyTwoWeekLow();
            if (hi > lo) {
                double posPct = 100.0 * (q.regularMarketPrice() - lo) / (hi - lo);
                if (posPct >= 93) {
                    score -= 1.1;
                } else if (posPct >= 88) {
                    score -= 0.5;
                } else if (posPct <= 12 && chg != null && chg > 0) {
                    score += 0.4;
                }
            }
        }

        if (chg != null && peers != null && !peers.isEmpty()) {
            int n = 0;
            int same = 0;
            for (SectorPeerMoveDto p : peers) {
                Double pc = p.regularMarketChangePercent();
                if (pc == null) {
                    continue;
                }
                n++;
                if (Math.signum(pc) == Math.signum(chg)) {
                    same++;
                }
            }
            if (n >= 2) {
                double frac = (double) same / (double) n;
                if (frac >= 0.66) {
                    score += 0.9;
                } else if (frac <= 0.34) {
                    score -= 0.5;
                }
            }
        }

        String tilt;
        if (score >= 1.75) {
            tilt = "Momentum-friendly next sessions (heuristic)";
        } else if (score <= -1.75) {
            tilt = "Fade or chop risk next sessions (heuristic)";
        } else {
            tilt = "Mixed — weak persistence signal (heuristic)";
        }

        String narrative =
                "Not sell-side or survey consensus, and not a forecast. This tilt blends rules from today's |%| move "
                        + "(very large one-day moves often show partial giveback or sideways digestion in many empirical "
                        + "samples), volume versus a rough average, headline tone versus session direction, where price "
                        + "sits in the 52-week band, and whether same-sector screener peers lean the same way. "
                        + "Heuristic label: "
                        + tilt
                        + ". Outcomes vary by catalyst and regime; use filings, guidance, and the live tape. Automated "
                        + "context only — not investment advice.";
        return new NearTermOutlook(tilt, narrative);
    }

    private record NearTermOutlook(String tilt, String narrative) {}

    private static double nvl(Double a, double d) {
        return a == null ? d : a;
    }

    private static Map<String, JsonNode> mergeScreenerQuotesByMaxAbsChg(JsonNode... roots) {
        Map<String, JsonNode> bySymbol = new HashMap<>();
        for (JsonNode root : roots) {
            if (root == null) {
                continue;
            }
            JsonNode quotes = firstResultQuotes(root);
            if (quotes == null || !quotes.isArray()) {
                continue;
            }
            for (JsonNode q : quotes) {
                String sym = screenerText(q, "symbol");
                if (sym == null || sym.isBlank()) {
                    continue;
                }
                String key = sym.trim().toUpperCase(Locale.ROOT);
                Double chg = dbl(q, "regularMarketChangePercent");
                double abs = chg == null ? 0.0 : Math.abs(chg);
                if (!bySymbol.containsKey(key)) {
                    bySymbol.put(key, q);
                } else {
                    Double oldC = dbl(bySymbol.get(key), "regularMarketChangePercent");
                    double oldA = oldC == null ? 0.0 : Math.abs(oldC);
                    if (abs > oldA) {
                        bySymbol.put(key, q);
                    }
                }
            }
        }
        return bySymbol;
    }

    private static List<String> pickTopSymbolsByAbsChg(Map<String, JsonNode> bySymbol, int n) {
        return bySymbol.entrySet().stream()
                .sorted(
                        Comparator.comparing(
                                (Map.Entry<String, JsonNode> e) -> {
                                    Double c = dbl(e.getValue(), "regularMarketChangePercent");
                                    return c == null ? 0.0 : Math.abs(c);
                                })
                                .reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    private JsonNode fetchScreenerJson(String scrId, int count) {
        try {
            return fetchScreenerCall(count, scrId);
        } catch (Exception e) {
            log.warn("Screener failed {}: {}", scrId, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode fetchScreenerJsonOptional(String scrId, int count) {
        return fetchScreenerJson(scrId, count);
    }

    private JsonNode fetchScreenerCall(int count, String scrId) {
        String url = String.format(Locale.ROOT, SCREENER_BASE, count, scrId);
        try {
            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.newsTimeoutMs())).build();
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .GET()
                            .timeout(Duration.ofMillis(props.newsTimeoutMs()))
                            .header("Accept", "application/json")
                            .header("User-Agent", "tracker-server/1.0")
                            .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("screener HTTP " + resp.statusCode());
            }
            return objectMapper.readTree(resp.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Screener request failed: {}", scrId, e);
            throw new IllegalStateException("screener " + scrId, e);
        }
    }

    private static JsonNode firstResultQuotes(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode finance = root.get("finance");
        if (finance == null) {
            return null;
        }
        JsonNode result = finance.get("result");
        if (result == null || !result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode first = result.get(0);
        return first != null ? first.get("quotes") : null;
    }

    private static String screenerText(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        String s = n.get(field).asText();
        return s == null ? null : s.trim();
    }

    private static Double dbl(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (v.isObject() && v.has("raw") && !v.get("raw").isNull()) {
            v = v.get("raw");
        }
        if (v.isNumber()) {
            return v.asDouble();
        }
        if (v.isTextual()) {
            String s = v.asText();
            if (s != null && !s.isEmpty()) {
                try {
                    return Double.parseDouble(s.trim());
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
