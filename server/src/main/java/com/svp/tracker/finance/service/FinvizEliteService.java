package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.domain.FinanceFinvizEliteSnapshot;
import com.svp.tracker.finance.dto.CompanyResearchUpsertRequestDto;
import com.svp.tracker.finance.dto.finviz.FinvizElitePresetDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteStatusDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteTableDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteWatchRequestDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteWatchResultDto;
import com.svp.tracker.finance.repository.FinanceFinvizEliteSnapshotRepository;
import com.svp.tracker.finance.service.FinvizEliteClient.CsvTable;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
public class FinvizEliteService {

    private static final String DEFAULT_VIEW = "111";
    private static final List<FinvizElitePresetDto> PRESETS = buildPresets();

    private final FinanceProperties props;
    private final FinvizEliteClient client;
    private final FinanceFinvizEliteSnapshotRepository snapshotRepository;
    private final CompanyResearchService companyResearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** In-flight coalescing: one live Elite fetch per cache key. */
    private final ConcurrentHashMap<String, Object> inflightLocks = new ConcurrentHashMap<>();

    public FinvizEliteStatusDto status() {
        boolean enabled = props.finvizEliteEnabled();
        boolean configured = props.finvizEliteConfigured();
        String note;
        if (!enabled) {
            note = "Set TRACKER_FINANCE_FINVIZ_ELITE_ENABLED=true and provide your Elite API key.";
        } else if (!configured) {
            note = "Finviz Elite is enabled but TRACKER_FINANCE_FINVIZ_ELITE_API_KEY is empty.";
        } else {
            note = "Finviz Elite export is ready.";
        }
        return new FinvizEliteStatusDto(enabled, configured, props.finvizEliteUniverseEnabled(), note);
    }

    public List<FinvizElitePresetDto> presets() {
        return PRESETS;
    }

    public FinvizEliteTableDto runPreset(String presetId, Integer limit, boolean forceRefresh) {
        FinvizElitePresetDto preset = findPreset(presetId);
        Map<String, String> q = new LinkedHashMap<>();
        q.put("v", blankTo(preset.view(), DEFAULT_VIEW));
        if (preset.signal() != null && !preset.signal().isBlank()) {
            q.put("s", preset.signal());
        }
        if (preset.filters() != null && !preset.filters().isBlank()) {
            q.put("f", preset.filters());
        }
        return fetchCached("preset:" + preset.id(), preset.label(), q, limit, forceRefresh);
    }

    public FinvizEliteTableDto runSignal(String signalName, Integer limit, boolean forceRefresh) {
        String signal = normalizeSignal(signalName);
        FinvizElitePresetDto match = PRESETS.stream()
                .filter(p -> signal.equalsIgnoreCase(p.signal()))
                .findFirst()
                .orElse(null);
        String label = match != null ? match.label() : signal;
        Map<String, String> q = new LinkedHashMap<>();
        q.put("v", "111");
        q.put("s", signal);
        return fetchCached("signal:" + signal.toLowerCase(Locale.ROOT), label, q, limit, forceRefresh);
    }

    public FinvizEliteTableDto runScreenerUrl(String rawUrl, Integer limit, boolean forceRefresh) {
        Map<String, String> q = parseScreenerUrlToExportParams(rawUrl);
        String label = "Custom screener";
        if (q.containsKey("s")) {
            label = "Signal " + q.get("s");
        } else if (q.containsKey("f")) {
            label = "Filters " + abbreviate(q.get("f"), 48);
        }
        String cacheKey = "url:" + stableQueryKey(q);
        return fetchCached(cacheKey, label, q, limit, forceRefresh);
    }

    public FinvizEliteTableDto groups(String groupBy, Integer limit, boolean forceRefresh) {
        // Groups export uses the groups page export shape; Elite accepts g=sector|industry via groups export.
        Map<String, String> q = new LinkedHashMap<>();
        String g = (groupBy == null || groupBy.isBlank()) ? "sector" : groupBy.trim().toLowerCase(Locale.ROOT);
        if (!g.equals("sector") && !g.equals("industry")) {
            g = "sector";
        }
        // Screener-style sector performance view as a reliable CSV export fallback.
        q.put("v", "141");
        if ("industry".equals(g)) {
            q.put("f", "ind_stocksonly");
            q.put("o", "-perf1w");
        } else {
            q.put("f", "sec_technology");
            q.put("o", "-perf1w");
        }
        // Prefer dedicated groups export when available.
        Map<String, String> groupsQ = new LinkedHashMap<>();
        groupsQ.put("g", g);
        groupsQ.put("v", "210");
        try {
            return fetchCached("groups:" + g, "Groups · " + g, groupsQ, limit, forceRefresh);
        } catch (ResponseStatusException ex) {
            log.warn("Finviz groups export failed ({}), falling back to screener view", ex.getReason());
            return fetchCached("groups-fallback:" + g, "Groups fallback · " + g, q, limit, forceRefresh);
        }
    }

    public FinvizEliteTableDto news(Integer limit, boolean forceRefresh) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("v", "320");
        q.put("s", "n_majornews");
        return fetchCached("news:major", "Major news", q, limit, forceRefresh);
    }

    public FinvizEliteTableDto options(String symbol, Integer limit, boolean forceRefresh) {
        String t = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (t.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol (t) is required");
        }
        Map<String, String> q = new LinkedHashMap<>();
        q.put("t", t);
        q.put("ty", "option");
        try {
            return fetchCached("options:" + t, "Options · " + t, q, limit, forceRefresh);
        } catch (ResponseStatusException ex) {
            // Fallback: overview row for the symbol so the UI still has something useful.
            Map<String, String> overview = new LinkedHashMap<>();
            overview.put("v", "111");
            overview.put("t", t);
            FinvizEliteTableDto table =
                    fetchCached("options-fallback:" + t, "Options unavailable · quote for " + t, overview, limit, true);
            return new FinvizEliteTableDto(
                    table.sourceLabel(),
                    table.columns(),
                    table.rows(),
                    table.fetchedAt(),
                    false,
                    "Options export failed ("
                            + (ex.getReason() == null ? "error" : ex.getReason())
                            + "). Showing symbol overview instead.");
        }
    }

    public FinvizEliteTableDto portfolio(Integer limit, boolean forceRefresh) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("v", "111");
        // Elite portfolio export historically uses pf= or similar; try portfolio view export.
        q.put("portfolio", "1");
        try {
            return fetchCached("portfolio:default", "Elite portfolio", q, limit, forceRefresh);
        } catch (ResponseStatusException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Portfolio export failed. Confirm Elite portfolio export works for your account: "
                            + (ex.getReason() == null ? ex.getMessage() : ex.getReason()));
        }
    }

    @Transactional
    public FinvizEliteWatchResultDto addToWatch(FinvizEliteWatchRequestDto req) {
        if (req == null || req.symbols() == null || req.symbols().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
        }
        String tag = req.thesisTag() == null || req.thesisTag().isBlank() ? "finviz-elite" : req.thesisTag().trim();
        List<String> done = new ArrayList<>();
        for (String raw : req.symbols()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String symbol = raw.trim().toUpperCase(Locale.ROOT);
            companyResearchService.upsert(
                    new CompanyResearchUpsertRequestDto(symbol, null, "WATCHING", List.of(tag), "Added from Finviz Elite"));
            done.add(symbol);
        }
        return new FinvizEliteWatchResultDto(done.size(), done);
    }

    /**
     * Universe for mid-cap / breakout when {@code finvizEliteUniverseEnabled}. Returns empty if Elite is off.
     */
    public List<String> universeTickers(String kind, int max) {
        if (!props.finvizEliteUniverseEnabled() || !props.finvizEliteConfigured()) {
            return List.of();
        }
        String filters =
                switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
                    case "breakout" -> "cap_midover,geo_usa,sh_avgvol_o400,ta_change_u,ta_highlow52w_a0to10h";
                    default -> "cap_midover,geo_usa,sh_avgvol_o200";
                };
        Map<String, String> q = new LinkedHashMap<>();
        q.put("v", "111");
        q.put("f", filters);
        q.put("o", "-marketcap");
        try {
            FinvizEliteTableDto table = fetchCached("universe:" + kind, "Universe " + kind, q, max, false);
            return extractTickers(table.rows(), max);
        } catch (Exception e) {
            log.warn("Finviz universe fetch failed for {}: {}", kind, e.toString());
            return List.of();
        }
    }

    private FinvizEliteTableDto fetchCached(
            String cacheKey, String label, Map<String, String> query, Integer limit, boolean forceRefresh) {
        int lim = sanitizeLimit(limit);
        Instant now = Instant.now();
        if (!forceRefresh) {
            Optional<FinvizEliteTableDto> cached = readCache(cacheKey, now);
            if (cached.isPresent()) {
                return truncate(cached.get(), lim);
            }
        }
        Object lock = inflightLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            try {
                if (!forceRefresh) {
                    Optional<FinvizEliteTableDto> cached = readCache(cacheKey, Instant.now());
                    if (cached.isPresent()) {
                        return truncate(cached.get(), lim);
                    }
                }
                CsvTable csv = client.export(query);
                FinvizEliteTableDto full = new FinvizEliteTableDto(
                        label,
                        csv.columns(),
                        csv.rows(),
                        Instant.now().toString(),
                        false,
                        null);
                writeCache(cacheKey, label, full);
                return truncate(full, lim);
            } finally {
                inflightLocks.remove(cacheKey, lock);
            }
        }
    }

    private Optional<FinvizEliteTableDto> readCache(String cacheKey, Instant now) {
        if (props.finvizEliteCacheTtlSeconds() <= 0) {
            return Optional.empty();
        }
        return snapshotRepository.findByCacheKey(cacheKey).flatMap(snap -> {
            if (snap.getExpiresAt() == null || snap.getExpiresAt().isBefore(now)) {
                return Optional.empty();
            }
            try {
                List<String> columns =
                        objectMapper.readValue(snap.getColumnsJson(), new TypeReference<>() {});
                List<Map<String, String>> rows =
                        objectMapper.readValue(snap.getRowsJson(), new TypeReference<>() {});
                return Optional.of(new FinvizEliteTableDto(
                        snap.getSourceLabel(),
                        columns,
                        rows,
                        snap.getFetchedAt() == null ? null : snap.getFetchedAt().toString(),
                        true,
                        null));
            } catch (Exception e) {
                log.warn("Corrupt Finviz snapshot {}: {}", cacheKey, e.toString());
                return Optional.empty();
            }
        });
    }

    private void writeCache(String cacheKey, String label, FinvizEliteTableDto table) {
        if (props.finvizEliteCacheTtlSeconds() <= 0) {
            return;
        }
        try {
            FinanceFinvizEliteSnapshot snap =
                    snapshotRepository.findByCacheKey(cacheKey).orElseGet(FinanceFinvizEliteSnapshot::new);
            snap.setCacheKey(cacheKey);
            snap.setSourceLabel(label);
            snap.setColumnsJson(objectMapper.writeValueAsString(table.columns()));
            snap.setRowsJson(objectMapper.writeValueAsString(table.rows()));
            Instant fetched = Instant.now();
            snap.setFetchedAt(fetched);
            snap.setExpiresAt(fetched.plusSeconds(props.finvizEliteCacheTtlSeconds()));
            snapshotRepository.save(snap);
        } catch (Exception e) {
            log.warn("Could not persist Finviz snapshot {}: {}", cacheKey, e.toString());
        }
    }

    private FinvizEliteTableDto truncate(FinvizEliteTableDto table, int limit) {
        if (table.rows().size() <= limit) {
            return table;
        }
        return new FinvizEliteTableDto(
                table.sourceLabel(),
                table.columns(),
                table.rows().subList(0, limit),
                table.fetchedAt(),
                table.fromCache(),
                table.note());
    }

    private FinvizElitePresetDto findPreset(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preset is required");
        }
        return PRESETS.stream()
                .filter(p -> p.id().equalsIgnoreCase(presetId.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown preset: " + presetId));
    }

    static Map<String, String> parseScreenerUrlToExportParams(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }
        String trimmed = rawUrl.trim();
        URI uri;
        try {
            if (!trimmed.contains("://")) {
                trimmed = "https://elite.finviz.com/" + trimmed.replaceFirst("^/+", "");
            }
            uri = URI.create(trimmed);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Finviz URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.contains("finviz.com")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must be a finviz.com screener link");
        }
        String query = uri.getRawQuery();
        Map<String, String> params = new LinkedHashMap<>();
        if (query != null && !query.isBlank()) {
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                if ("auth".equalsIgnoreCase(k)) {
                    continue;
                }
                params.put(k, v);
            }
        }
        if (!params.containsKey("v")) {
            params.put("v", DEFAULT_VIEW);
        }
        return params;
    }

    private static String normalizeSignal(String signalName) {
        if (signalName == null || signalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signal name is required");
        }
        String s = signalName.trim();
        // Accept both ta_topgainers and topgainers
        if (!s.contains("_")) {
            s = "ta_" + s;
        }
        return s;
    }

    private static int sanitizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 50;
        }
        return Math.min(200, limit);
    }

    private static String blankTo(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String stableQueryKey(Map<String, String> q) {
        return q.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    static List<String> extractTickers(List<Map<String, String>> rows, int max) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String t = firstTicker(row);
            if (t != null) {
                out.add(t);
            }
            if (out.size() >= max) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static String firstTicker(Map<String, String> row) {
        for (String key : List.of("Ticker", "Symbol", "ticker", "symbol")) {
            String v = row.get(key);
            if (v != null && !v.isBlank()) {
                return v.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static List<FinvizElitePresetDto> buildPresets() {
        List<FinvizElitePresetDto> list = new ArrayList<>();
        list.add(signal("top-gainers", "Top gainers", "ta_topgainers"));
        list.add(signal("top-losers", "Top losers", "ta_toplosers"));
        list.add(signal("unusual-volume", "Unusual volume", "ta_unusualvolume"));
        list.add(signal("most-active", "Most active", "ta_mostactive"));
        list.add(signal("most-volatile", "Most volatile", "ta_mostvolatile"));
        list.add(signal("new-high", "New high", "ta_newhigh"));
        list.add(signal("new-low", "New low", "ta_newlow"));
        list.add(signal("overbought", "Overbought", "ta_overbought"));
        list.add(signal("oversold", "Oversold", "ta_oversold"));
        list.add(signal("upgrades", "Upgrades", "n_upgrades"));
        list.add(signal("downgrades", "Downgrades", "n_downgrades"));
        list.add(signal("major-news", "Major news", "n_majornews"));
        list.add(signal("insider-buys", "Insider buys", "it_latestbuys"));
        list.add(signal("insider-sales", "Insider sales", "it_latestsales"));
        list.add(new FinvizElitePresetDto(
                "mid-usa-momentum",
                "Mid+ USA momentum",
                "filter",
                "Mid-cap+, USA, liquid, up on the day",
                null,
                "cap_midover,geo_usa,sh_avgvol_o400,ta_change_u",
                "111"));
        list.add(new FinvizElitePresetDto(
                "mid-usa-value",
                "Mid+ USA value",
                "filter",
                "Mid-cap+, USA, P/E under 20, positive EPS growth",
                null,
                "cap_midover,geo_usa,fa_pe_u20,fa_epsyoy_pos",
                "121"));
        list.add(new FinvizElitePresetDto(
                "near-52w-high",
                "Near 52-week high",
                "filter",
                "Mid-cap+, USA, within 10% of 52w high",
                null,
                "cap_midover,geo_usa,ta_highlow52w_a0to10h,sh_avgvol_o200",
                "111"));
        return List.copyOf(list);
    }

    private static FinvizElitePresetDto signal(String id, String label, String signal) {
        return new FinvizElitePresetDto(id, label, "signal", "Elite homepage signal", signal, null, "111");
    }
}
