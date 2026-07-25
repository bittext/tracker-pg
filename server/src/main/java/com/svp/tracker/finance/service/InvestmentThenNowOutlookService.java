package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.FinanceInvestmentThenNow;
import com.svp.tracker.finance.domain.FinanceInvestmentThenNowOutlook;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto.ForwardPoint;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto.InvestmentThenNowOutlookSymbolDto;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto.ScenarioBand;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookRequestDto;
import com.svp.tracker.finance.dto.StockNewsDto;
import com.svp.tracker.finance.dto.StockNewsItemDto;
import com.svp.tracker.finance.repository.FinanceInvestmentThenNowOutlookRepository;
import com.svp.tracker.finance.repository.FinanceInvestmentThenNowRepository;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Speculative AI outlook for saved Then & now scenarios (news + fundamentals + chat). Educational only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentThenNowOutlookService {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final String DISCLAIMER =
            "Speculative, model-generated educational scenario — not investment advice, not a recommendation to buy "
                    + "or sell, and not a guarantee of future results. Markets can move opposite these illustrations.";
    private static final String SYSTEM_PROMPT =
            """
            You are a careful markets research assistant writing speculative educational scenarios.
            Given per-symbol context (saved investment replay, recent headlines, optional fundamentals),
            return JSON ONLY with this shape:
            {
              "summary": "short cross-symbol overview",
              "symbols": [
                {
                  "symbol": "AAPL",
                  "thesis": "2-4 sentences",
                  "bull": { "narrative": "...", "targetPrice": 200.0, "probabilityHint": "e.g. 25%" },
                  "base": { "narrative": "...", "targetPrice": 180.0, "probabilityHint": "e.g. 50%" },
                  "bear": { "narrative": "...", "targetPrice": 150.0, "probabilityHint": "e.g. 25%" },
                  "catalysts": ["..."],
                  "risks": ["..."],
                  "forwardBase": [{"date":"YYYY-MM-DD","price":180.0}],
                  "forwardBull": [{"date":"YYYY-MM-DD","price":190.0}],
                  "forwardBear": [{"date":"YYYY-MM-DD","price":160.0}]
                }
              ]
            }
            Rules:
            - Horizon is given in months; produce about one forward point per month from next month through horizon.
            - Anchor paths near the provided lastClose; do not invent impossible jumps without stating uncertainty.
            - Be honest about uncertainty. Prefer plausible ranges over hype.
            - Include every symbol from the context. Do not add symbols not listed.
            - This is speculative fiction for learning — never claim certainty.
            """;

    private final CurrentUserService currentUser;
    private final FinanceProperties props;
    private final RobinhoodRhDailyTrackerProperties rhDailyProps;
    private final FinanceInvestmentThenNowRepository scenarioRepository;
    private final FinanceInvestmentThenNowOutlookRepository outlookRepository;
    private final StockNewsService stockNewsService;
    private final RhDailyTrackerOpenAiClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    /** ownerUserId → last successful generate Instant (in-memory rate limit). */
    private final Map<Long, Instant> lastGenerateAt = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public InvestmentThenNowOutlookDto getCached() {
        long owner = currentUser.requireUserId();
        return outlookRepository
                .findByOwnerUserId(owner)
                .map(row -> fromStored(row, true))
                .orElse(null);
    }

    @Transactional
    public InvestmentThenNowOutlookDto generate(InvestmentThenNowOutlookRequestDto body) {
        long owner = currentUser.requireUserId();
        boolean force = body != null && Boolean.TRUE.equals(body.force());
        int horizon = normalizeHorizon(body == null ? null : body.horizonMonths());

        Instant last = lastGenerateAt.get(owner);
        if (!force && last != null && last.plus(Duration.ofMinutes(1)).isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Outlook generation is rate-limited to once per minute. Wait a moment or load the cached outlook.");
        }

        List<FinanceInvestmentThenNow> scenarios = resolveScenarios(owner, body == null ? null : body.scenarioIds());
        if (scenarios.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Save at least one Then & now answer before generating an outlook");
        }

        if (!force) {
            var cached = outlookRepository.findByOwnerUserId(owner);
            if (cached.isPresent()
                    && cached.get().getHorizonMonths() == horizon
                    && scenarioKey(scenarios).equals(cached.get().getScenarioIds())) {
                return fromStored(cached.get(), true);
            }
        }

        String contextJson = buildContextJson(scenarios, horizon);
        String completion = openAiClient.completeJson(
                SYSTEM_PROMPT,
                "HorizonMonths=" + horizon + "\nContext JSON:\n" + contextJson,
                Math.max(rhDailyProps.ai().maxOutputTokens(), 4_000));
        InvestmentThenNowOutlookDto parsed = parseCompletion(completion, scenarios, horizon);

        Instant now = Instant.now();
        FinanceInvestmentThenNowOutlook row =
                outlookRepository.findByOwnerUserId(owner).orElseGet(FinanceInvestmentThenNowOutlook::new);
        if (row.getId() == null) {
            row.setOwnerUserId(owner);
            row.setCreatedAt(now);
        }
        row.setHorizonMonths(horizon);
        row.setModel(nullToEmpty(rhDailyProps.ai().model()));
        try {
            row.setOutlookJson(objectMapper.writeValueAsString(parsed));
        } catch (Exception e) {
            log.error("Could not serialize outlook JSON for owner={}", owner, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Could not persist outlook: " + e.getMessage());
        }
        row.setScenarioIds(scenarioKey(scenarios));
        row.setGeneratedAt(now);
        row.setUpdatedAt(now);
        outlookRepository.save(row);
        lastGenerateAt.put(owner, now);
        return new InvestmentThenNowOutlookDto(
                DISCLAIMER,
                horizon,
                parsed.summary(),
                row.getModel(),
                now,
                false,
                parsed.symbols());
    }

    private List<FinanceInvestmentThenNow> resolveScenarios(long owner, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return scenarioRepository.findByOwnerUserIdOrderByUpdatedAtDesc(owner);
        }
        List<Long> distinct = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinct.isEmpty()) {
            return scenarioRepository.findByOwnerUserIdOrderByUpdatedAtDesc(owner);
        }
        return scenarioRepository.findByOwnerUserIdAndIdIn(owner, distinct).stream()
                .sorted(Comparator.comparing(FinanceInvestmentThenNow::getUpdatedAt).reversed())
                .toList();
    }

    private static String scenarioKey(List<FinanceInvestmentThenNow> scenarios) {
        return scenarios.stream()
                .map(s -> String.valueOf(s.getId()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static int normalizeHorizon(Integer months) {
        if (months == null) {
            return 6;
        }
        if (months == 3 || months == 6 || months == 12) {
            return months;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "horizonMonths must be 3, 6, or 12");
    }

    private String buildContextJson(List<FinanceInvestmentThenNow> scenarios, int horizon) {
        try {
            ArrayNode arr = objectMapper.createArrayNode();
            for (FinanceInvestmentThenNow row : scenarios) {
                ObjectNode n = arr.addObject();
                n.put("scenarioId", row.getId());
                n.put("symbol", row.getSymbol());
                n.put("companyName", row.getCompanyName());
                n.put("investedAmount", row.getInvestedAmount());
                n.put("asOfDate", row.getAsOfDate().toString());
                n.put("shares", row.getShares());
                n.put("lastClose", row.getPriceNow());
                n.put("priceAsOf", row.getPriceAsOfDate());
                n.put("gainPercent", row.getGainPercent());
                n.put("worthNow", row.getWorthNow());
                n.put("horizonMonths", horizon);

                ArrayNode headlines = n.putArray("headlines");
                try {
                    StockNewsDto news =
                            stockNewsService.fetchLatestNews(row.getSymbol(), row.getCompanyName(), 6);
                    if (news != null && news.items() != null) {
                        for (StockNewsItemDto item : news.items()) {
                            if (item == null || item.title() == null || item.title().isBlank()) {
                                continue;
                            }
                            ObjectNode h = headlines.addObject();
                            h.put("title", item.title());
                            h.put("source", nullToEmpty(item.source()));
                            h.put("publishedAt", nullToEmpty(item.publishedAt()));
                            String summary = item.summary();
                            if (summary != null && !summary.isBlank()) {
                                h.put(
                                        "summary",
                                        summary.length() > 280 ? summary.substring(0, 280) + "…" : summary);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("then/now outlook news failed for {}: {}", row.getSymbol(), e.toString());
                    n.put("newsError", e.getMessage() == null ? "news fetch failed" : e.getMessage());
                }

                ObjectNode overview = fetchAlphaOverview(row.getSymbol());
                if (overview != null) {
                    n.set("fundamentals", overview);
                }
            }
            return objectMapper.writeValueAsString(arr);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build outlook context");
        }
    }

    private ObjectNode fetchAlphaOverview(String symbol) {
        String key = props.alphaVantageApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            String url = props.alphaVantageBaseUrl()
                    + "?function=OVERVIEW&symbol="
                    + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                    + "&apikey="
                    + URLEncoder.encode(key, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(props.newsTimeoutMs(), 15_000)))
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.isObject() || root.has("Note") || root.has("Information") || root.has("Error Message")) {
                return null;
            }
            ObjectNode out = objectMapper.createObjectNode();
            copyText(root, out, "Name", "name");
            copyText(root, out, "Description", "description");
            copyText(root, out, "Sector", "sector");
            copyText(root, out, "Industry", "industry");
            copyText(root, out, "MarketCapitalization", "marketCap");
            copyText(root, out, "PERatio", "peRatio");
            copyText(root, out, "PEGRatio", "pegRatio");
            copyText(root, out, "ProfitMargin", "profitMargin");
            copyText(root, out, "OperatingMarginTTM", "operatingMargin");
            copyText(root, out, "ReturnOnEquityTTM", "roe");
            copyText(root, out, "RevenueTTM", "revenueTtm");
            copyText(root, out, "EPS", "eps");
            copyText(root, out, "52WeekHigh", "week52High");
            copyText(root, out, "52WeekLow", "week52Low");
            copyText(root, out, "AnalystTargetPrice", "analystTarget");
            // Trim long descriptions for token budget.
            if (out.has("description")) {
                String d = out.get("description").asText("");
                if (d.length() > 600) {
                    out.put("description", d.substring(0, 600) + "…");
                }
            }
            return out.size() == 0 ? null : out;
        } catch (Exception e) {
            log.warn("Alpha Vantage OVERVIEW failed for {}: {}", symbol, e.toString());
            return null;
        }
    }

    private static void copyText(JsonNode from, ObjectNode to, String fromField, String toField) {
        JsonNode n = from.get(fromField);
        if (n == null || n.isNull()) {
            return;
        }
        String v = n.asText("").trim();
        if (!v.isBlank() && !"None".equalsIgnoreCase(v) && !"-".equals(v)) {
            to.put(toField, v);
        }
    }

    private InvestmentThenNowOutlookDto parseCompletion(
            String completion, List<FinanceInvestmentThenNow> scenarios, int horizon) {
        try {
            JsonNode root = objectMapper.readTree(completion);
            String summary = text(root, "summary");
            JsonNode symbolsNode = root.path("symbols");
            Map<String, FinanceInvestmentThenNow> bySymbol = scenarios.stream()
                    .collect(Collectors.toMap(
                            s -> s.getSymbol().toUpperCase(Locale.ROOT), s -> s, (a, b) -> a));
            List<InvestmentThenNowOutlookSymbolDto> symbols = new ArrayList<>();
            if (symbolsNode.isArray()) {
                for (JsonNode s : symbolsNode) {
                    String symbol = text(s, "symbol").toUpperCase(Locale.ROOT);
                    if (symbol.isBlank()) {
                        continue;
                    }
                    FinanceInvestmentThenNow row = bySymbol.get(symbol);
                    symbols.add(new InvestmentThenNowOutlookSymbolDto(
                            symbol,
                            row != null ? row.getCompanyName() : symbol,
                            row != null ? row.getId() : null,
                            text(s, "thesis"),
                            parseBand(s.path("bull")),
                            parseBand(s.path("base")),
                            parseBand(s.path("bear")),
                            stringList(s, "catalysts"),
                            stringList(s, "risks"),
                            parseForward(s.path("forwardBase"), horizon),
                            parseForward(s.path("forwardBull"), horizon),
                            parseForward(s.path("forwardBear"), horizon)));
                }
            }
            // Ensure every requested symbol appears even if the model omitted one.
            for (FinanceInvestmentThenNow row : scenarios) {
                boolean present = symbols.stream().anyMatch(s -> s.symbol().equalsIgnoreCase(row.getSymbol()));
                if (!present) {
                    symbols.add(stubSymbol(row, horizon));
                }
            }
            return new InvestmentThenNowOutlookDto(
                    DISCLAIMER,
                    horizon,
                    summary.isBlank() ? "Speculative outlook generated." : summary,
                    "",
                    Instant.now(),
                    false,
                    List.copyOf(symbols));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse AI outlook JSON");
        }
    }

    private InvestmentThenNowOutlookSymbolDto stubSymbol(FinanceInvestmentThenNow row, int horizon) {
        BigDecimal last = row.getPriceNow();
        List<ForwardPoint> flat = flatForward(last, horizon);
        ScenarioBand band = new ScenarioBand(
                "Model omitted this symbol; showing a flat path from the last close.", last, "n/a");
        return new InvestmentThenNowOutlookSymbolDto(
                row.getSymbol(),
                row.getCompanyName(),
                row.getId(),
                "Insufficient model output for this symbol.",
                band,
                band,
                band,
                List.of(),
                List.of("AI response incomplete for this ticker"),
                flat,
                flat,
                flat);
    }

    private static List<ForwardPoint> flatForward(BigDecimal last, int horizon) {
        LocalDate start = LocalDate.now(NY).withDayOfMonth(1).plusMonths(1);
        List<ForwardPoint> out = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            out.add(new ForwardPoint(start.plusMonths(i), last));
        }
        return out;
    }

    private static ScenarioBand parseBand(JsonNode n) {
        if (n == null || !n.isObject()) {
            return new ScenarioBand("", null, "");
        }
        return new ScenarioBand(text(n, "narrative"), decimal(n.get("targetPrice")), text(n, "probabilityHint"));
    }

    private static List<ForwardPoint> parseForward(JsonNode n, int horizon) {
        if (n == null || !n.isArray()) {
            return List.of();
        }
        List<ForwardPoint> out = new ArrayList<>();
        for (JsonNode p : n) {
            String dateText = text(p, "date");
            BigDecimal price = decimal(p.get("price"));
            if (dateText.isBlank() || price == null) {
                continue;
            }
            try {
                out.add(new ForwardPoint(LocalDate.parse(dateText), price));
            } catch (DateTimeParseException ignored) {
                // skip
            }
            if (out.size() >= horizon + 2) {
                break;
            }
        }
        out.sort(Comparator.comparing(ForwardPoint::date));
        return List.copyOf(out);
    }

    private InvestmentThenNowOutlookDto fromStored(FinanceInvestmentThenNowOutlook row, boolean cached) {
        try {
            InvestmentThenNowOutlookDto parsed =
                    objectMapper.readValue(row.getOutlookJson(), InvestmentThenNowOutlookDto.class);
            return new InvestmentThenNowOutlookDto(
                    DISCLAIMER,
                    row.getHorizonMonths(),
                    parsed.summary() == null ? "" : parsed.summary(),
                    row.getModel(),
                    row.getGeneratedAt(),
                    cached,
                    parsed.symbols() == null ? List.of() : parsed.symbols());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Cached outlook is corrupt; regenerate with force=true");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? "" : n.asText("").trim();
    }

    private static List<String> stringList(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || !n.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : n) {
            String s = item.asText("").trim();
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    private static BigDecimal decimal(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        try {
            if (n.isNumber()) {
                return BigDecimal.valueOf(n.asDouble()).setScale(4, RoundingMode.HALF_UP);
            }
            String t = n.asText("").replace("$", "").replace(",", "").trim();
            if (t.isBlank()) {
                return null;
            }
            return new BigDecimal(t).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
