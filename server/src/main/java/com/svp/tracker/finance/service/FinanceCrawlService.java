package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.CrawlerWatchItemDto;
import com.svp.tracker.finance.dto.FinanceCrawlSnapshotDto;
import com.svp.tracker.finance.dto.IndexSnapshotDto;
import com.svp.tracker.finance.dto.StockNewsAnalysisDto;
import com.svp.tracker.finance.dto.StockNewsDto;
import com.svp.tracker.finance.dto.StockNewsItemDto;
import com.svp.tracker.finance.dto.StockNewsStressSignalsDto;
import com.svp.tracker.finance.dto.YahooSimpleQuoteDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Assembles the Finance “Crawler” tab: two broad Google News topic crawls, Yahoo batch marks for the stock + major
 * indexes, and a deeper per-symbol news pass for a small watch list.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceCrawlService {
    private static final String Q_GENERAL = "(top stories OR world news OR breaking news) when:2d";
    private static final String Q_FINANCIAL =
            "(stock market OR Wall Street OR Federal Reserve OR S&P 500 OR economy) when:3d";
    private static final List<Watch> WATCH = List.of(
            new Watch("HOOD", "Robinhood", "HOOD \"Robinhood\" stock when:7d"),
            new Watch("CRWV", "CoreWeave", "CRWV \"CoreWeave\" stock when:7d"),
            new Watch("NBIS", "Nebius", "NBIS \"Nebius\" stock when:7d"));
    private static final List<String> INDEXES = List.of("SPY", "QQQ", "DIA", "IWM");

    private final FinanceProperties props;
    private final StockNewsService stockNewsService;
    private final YahooBatchQuoteService yahooBatchQuoteService;

    public FinanceCrawlSnapshotDto buildSnapshot() {
        if (!props.newsEnabled()) {
            throw new IllegalStateException("Stock news is disabled (tracker.finance.news-enabled=false)");
        }
        int limit = deepLimit();
        String note =
                "Headlines: Google News RSS, trusted sources only, "
                        + limit
                        + " per block. Quotes: Yahoo Finance (regular session, delayed on some symbols). Not investment"
                        + " advice.";

        StockNewsDto general = safeTopic("General purpose", Q_GENERAL, limit);
        StockNewsDto financial = safeTopic("Financial markets", Q_FINANCIAL, limit);

        List<String> allSyms = new ArrayList<>();
        for (Watch w : WATCH) {
            allSyms.add(w.symbol);
        }
        allSyms.addAll(INDEXES);
        Map<String, YahooSimpleQuoteDto> quotes = yahooBatchQuoteService.fetchBySymbols(allSyms);

        List<IndexSnapshotDto> indexRows = new ArrayList<>();
        for (String s : INDEXES) {
            YahooSimpleQuoteDto q = quotes.get(s);
            if (q != null) {
                indexRows.add(new IndexSnapshotDto(s, q.shortName(), q.regularMarketPrice(), q.regularMarketChangePercent()));
            }
        }
        indexRows.sort(Comparator.comparing(IndexSnapshotDto::symbol));

        List<CrawlerWatchItemDto> watch = new ArrayList<>();
        for (Watch w : WATCH) {
            watch.add(buildWatch(w, limit, quotes));
        }

        return new FinanceCrawlSnapshotDto(
                Instant.now().toString(), note, limit, general, financial, indexRows, watch);
    }

    private int deepLimit() {
        int cap = Math.max(1, props.newsMaxItems());
        return Math.max(20, Math.min(50, cap * 2));
    }

    private StockNewsDto safeTopic(String label, String query, int limit) {
        try {
            return stockNewsService.fetchTopicHeadlines(label, query, limit);
        } catch (Exception e) {
            log.warn("Topic crawl failed: {}", label, e);
            return failedNews(label, e.getMessage() == null ? "unknown error" : e.getMessage());
        }
    }

    private CrawlerWatchItemDto buildWatch(Watch w, int limit, Map<String, YahooSimpleQuoteDto> quotes) {
        List<String> warn = new ArrayList<>();
        StockNewsDto news;
        try {
            news = stockNewsService.fetchLatestNews(w.symbol, w.company, limit);
        } catch (Exception e) {
            log.warn("Watch news failed: {}", w.symbol, e);
            warn.add("News: " + (e.getMessage() == null ? "unavailable" : e.getMessage()));
            news = failedNews(w.company, e.getMessage() == null ? "error" : e.getMessage());
        }
        YahooSimpleQuoteDto q = quotes.get(w.symbol);
        if (q == null) {
            warn.add("No Yahoo quote row for " + w.symbol);
        }
        String vs = summarizeVsIndexes(w.symbol, q, quotes);
        String analysis = summarizeAnalysis(news);
        return new CrawlerWatchItemDto(
                w.symbol, w.company, w.queryNote, news, q, vs, analysis, List.copyOf(warn));
    }

    private static String summarizeAnalysis(StockNewsDto n) {
        if (n == null || n.analysis() == null) {
            return "—";
        }
        var a = n.analysis();
        return String.format(
                Locale.ROOT,
                "Sentiment %s; outlook %s; deal/regulatory stress %s (heuristic, not a forecast).",
                a.overallSentiment(),
                a.projectedGrowthLabel(),
                a.stressSignals() != null ? a.stressSignals().emphasis() : "—");
    }

    private static String summarizeVsIndexes(
            String stockSym, YahooSimpleQuoteDto stock, Map<String, YahooSimpleQuoteDto> quotes) {
        if (stock == null || stock.regularMarketChangePercent() == null) {
            return "Session change unavailable; compare after quotes load.";
        }
        double s = stock.regularMarketChangePercent();
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "%s regular session %+,.2f%%. ", stockSym, s));
        List<String> parts = new ArrayList<>();
        for (String idx : INDEXES) {
            YahooSimpleQuoteDto iq = quotes.get(idx);
            if (iq == null || iq.regularMarketChangePercent() == null) {
                continue;
            }
            double p = iq.regularMarketChangePercent();
            double gap = s - p;
            String edge = gap >= 0.01 ? "ahead of" : gap <= -0.01 ? "behind" : "in line with";
            parts.add(String.format(Locale.ROOT, "%s %+,.2f%% (%s by %+.2fpp)", idx, p, edge, gap));
        }
        if (parts.isEmpty()) {
            b.append("Index marks unavailable for comparison.");
        } else {
            b.append(String.join("; ", parts)).append(".");
        }
        return b.toString();
    }

    private static StockNewsDto failedNews(String label, String err) {
        StockNewsAnalysisDto analysis =
                new StockNewsAnalysisDto(
                        "Neutral",
                        0.0,
                        50,
                        "Sideways",
                        new StockNewsStressSignalsDto(0, 0, 0, 0, 0, "Low"));
        return new StockNewsDto(
                "TOPIC",
                label,
                0,
                0,
                "Google News RSS",
                Instant.now().toString(),
                "Load failed: " + err,
                analysis,
                List.<StockNewsItemDto>of());
    }

    private record Watch(String symbol, String company, String queryNote) {}
}
