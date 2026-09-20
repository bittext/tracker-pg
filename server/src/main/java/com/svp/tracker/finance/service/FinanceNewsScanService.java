package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.domain.FinanceNewsScanTicker;
import com.svp.tracker.finance.dto.FinanceNewsScanDto;
import com.svp.tracker.finance.dto.FinanceNewsScanHitDto;
import com.svp.tracker.finance.dto.FinanceNewsScanTickersRequestDto;
import com.svp.tracker.finance.dto.StockNewsDto;
import com.svp.tracker.finance.dto.StockNewsItemDto;
import com.svp.tracker.finance.repository.FinanceNewsScanTickerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceNewsScanService {

    static final int MAX_SYMBOLS = 12;
    static final Duration RECENT_WINDOW = Duration.ofHours(36);
    static final Duration CACHE_TTL = Duration.ofMinutes(8);
    static final Pattern SYMBOL = Pattern.compile("^[A-Z][A-Z0-9.\\-]{0,15}$");

    private final CurrentUserService currentUser;
    private final FinanceNewsScanTickerRepository tickerRepository;
    private final StockNewsService stockNewsService;
    private final FinanceProperties props;
    private final PlatformTransactionManager transactionManager;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(FinanceNewsScanDto snapshot, Instant expiresAt) {}

    public FinanceNewsScanDto scanCurrentUser(boolean forceRefresh) {
        long owner = currentUser.requireUserId();
        List<String> tickers = loadSymbols(owner);
        if (!forceRefresh) {
            CacheEntry hit = cache.get(owner);
            if (hit != null && hit.expiresAt().isAfter(Instant.now()) && hit.snapshot().tickers().equals(tickers)) {
                return hit.snapshot();
            }
        }
        FinanceNewsScanDto snapshot = buildScan(tickers);
        cache.put(owner, new CacheEntry(snapshot, Instant.now().plus(CACHE_TTL)));
        return snapshot;
    }

    public FinanceNewsScanDto replaceCurrentUserTickers(FinanceNewsScanTickersRequestDto req) {
        long owner = currentUser.requireUserId();
        List<String> symbols = parseTickers(req == null ? null : req.tickers());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            tickerRepository.deleteByOwnerUserId(owner);
            int order = 0;
            for (String symbol : symbols) {
                FinanceNewsScanTicker row = new FinanceNewsScanTicker();
                row.setOwnerUserId(owner);
                row.setSymbol(symbol);
                row.setSortOrder(order++);
                tickerRepository.save(row);
            }
        });
        cache.remove(owner);
        return scanCurrentUser(true);
    }

    static List<String> parseTickers(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\s]+")) {
            String symbol = part.trim().toUpperCase(Locale.ROOT);
            if (symbol.isEmpty()) {
                continue;
            }
            if (!SYMBOL.matcher(symbol).matches()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid ticker: " + symbol + " (letters, numbers, dot, or dash)");
            }
            out.add(symbol);
            if (out.size() > MAX_SYMBOLS) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "At most " + MAX_SYMBOLS + " tickers on the news scan list");
            }
        }
        return List.copyOf(out);
    }

    static boolean publishedWithin(String publishedAt, Instant now, Duration window) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return true;
        }
        try {
            Instant published = Instant.parse(publishedAt.trim());
            return !published.isBefore(now.minus(window));
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private List<String> loadSymbols(long owner) {
        return tickerRepository.findByOwnerUserIdOrderBySortOrderAscIdAsc(owner).stream()
                .map(FinanceNewsScanTicker::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    private FinanceNewsScanDto buildScan(List<String> tickers) {
        Instant fetchedAt = Instant.now();
        if (!props.newsEnabled()) {
            return new FinanceNewsScanDto(
                    false, tickers, fetchedAt.toString(), "Stock news is disabled.", List.of());
        }
        if (tickers.isEmpty()) {
            return new FinanceNewsScanDto(
                    true,
                    tickers,
                    fetchedAt.toString(),
                    "Add tickers to scan today’s headlines.",
                    List.of());
        }
        List<FinanceNewsScanHitDto> hits = scanSymbols(tickers, fetchedAt);
        return new FinanceNewsScanDto(
                true,
                tickers,
                fetchedAt.toString(),
                hits.isEmpty()
                        ? "No headlines in the last day for the saved tickers."
                        : "Today’s headlines for the saved ticker list.",
                hits);
    }

    private List<FinanceNewsScanHitDto> scanSymbols(List<String> tickers, Instant now) {
        int poolSize = Math.min(tickers.size(), 6);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {
            Map<String, Future<FinanceNewsScanHitDto>> futures = new java.util.LinkedHashMap<>();
            for (String symbol : tickers) {
                futures.put(symbol, pool.submit(() -> scanOne(symbol, now)));
            }
            List<FinanceNewsScanHitDto> hits = new ArrayList<>();
            for (Map.Entry<String, Future<FinanceNewsScanHitDto>> entry : futures.entrySet()) {
                try {
                    long waitSec = Math.max(8, props.newsTimeoutMs() / 1000L + 4);
                    FinanceNewsScanHitDto hit = entry.getValue().get(waitSec, TimeUnit.SECONDS);
                    if (hit != null) {
                        hits.add(hit);
                    }
                } catch (Exception e) {
                    log.warn("News scan failed for {}", entry.getKey(), e);
                }
            }
            return hits;
        } finally {
            pool.shutdownNow();
        }
    }

    private FinanceNewsScanHitDto scanOne(String symbol, Instant now) {
        StockNewsDto news;
        try {
            news = stockNewsService.fetchLatestNews(symbol, null, 8, "1d", false);
        } catch (RuntimeException e) {
            log.warn("Day news fetch failed for {}", symbol, e);
            return null;
        }
        List<StockNewsItemDto> recent = news.items() == null
                ? List.of()
                : news.items().stream().filter(item -> publishedWithin(item.publishedAt(), now, RECENT_WINDOW)).toList();
        if (recent.isEmpty()) {
            return null;
        }
        StockNewsItemDto latest = recent.get(0);
        boolean trusted = stockNewsService.isTrustedOutlet(latest.source(), latest.url());
        return new FinanceNewsScanHitDto(
                symbol,
                latest.title(),
                latest.source(),
                latest.url(),
                latest.publishedAt(),
                trusted,
                recent.size());
    }
}
