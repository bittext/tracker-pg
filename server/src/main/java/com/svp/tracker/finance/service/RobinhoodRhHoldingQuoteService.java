package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.WebullQuoteProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhLiveQuotesDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Fetches live Robinhood MCP quotes with Webull OpenAPI fallback for RH holdings displays. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhHoldingQuoteService {

    private final RobinhoodAgenticProperties agenticProps;
    private final WebullQuoteProperties webullProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final WebullQuoteSidecarClient webullQuoteClient;

    public RobinhoodRhLiveQuotesDto fetchForHoldings(
            long ownerUserId, List<RobinhoodRhHoldingDto> holdings, List<RobinhoodAgenticPosition> positions) {
        List<RobinhoodRhHoldingDto> safeHoldings = holdings == null ? List.of() : holdings;
        List<RobinhoodAgenticPosition> safePositions = positions == null ? List.of() : positions;
        if (safeHoldings.isEmpty() && safePositions.isEmpty()) {
            return RobinhoodRhLiveQuotesDto.empty();
        }

        QuoteKeys keys = collectQuoteKeys(safeHoldings, safePositions);
        RobinhoodRhLiveQuotesDto webull = fetchWebullQuotes(keys, safePositions);
        RobinhoodRhLiveQuotesDto robinhood = fetchRobinhoodQuotes(ownerUserId, keys);
        return mergeQuotes(webull, robinhood);
    }

    private QuoteKeys collectQuoteKeys(
            List<RobinhoodRhHoldingDto> holdings, List<RobinhoodAgenticPosition> positions) {
        Set<String> equitySymbols = new LinkedHashSet<>();
        Set<String> optionInstrumentIds = new LinkedHashSet<>();
        Map<String, String> instrumentIdByMatchKey = instrumentIdsByMatchKey(positions);

        for (RobinhoodRhHoldingDto h : holdings) {
            collectQuoteKeys(h, equitySymbols, instrumentIdByMatchKey, optionInstrumentIds);
        }
        for (RobinhoodAgenticPosition p : positions) {
            if ("equity".equalsIgnoreCase(p.getPositionType())
                    && p.getSymbol() != null
                    && !p.getSymbol().isBlank()) {
                equitySymbols.add(p.getSymbol().trim().toUpperCase(Locale.ROOT));
            }
            if ("option".equalsIgnoreCase(p.getPositionType())) {
                String instrumentId = optionInstrumentId(p.getPositionKey());
                if (instrumentId != null) {
                    optionInstrumentIds.add(instrumentId);
                }
            }
        }
        return new QuoteKeys(List.copyOf(equitySymbols), List.copyOf(optionInstrumentIds));
    }

    private RobinhoodRhLiveQuotesDto fetchRobinhoodQuotes(long ownerUserId, QuoteKeys keys) {
        if (!agenticProps.serviceConfigured()
                || (keys.equitySymbols().isEmpty() && keys.optionInstrumentIds().isEmpty())) {
            return RobinhoodRhLiveQuotesDto.empty();
        }
        Optional<RobinhoodAgenticConnection> conn = connectionRepository
                .findByOwnerUserId(ownerUserId)
                .filter(c -> c.getAccessToken() != null && !c.getAccessToken().isBlank());
        if (conn.isEmpty()) {
            return RobinhoodRhLiveQuotesDto.empty();
        }
        try {
            JsonNode result = tokenService.fetchHoldingsQuotes(
                    conn.get(), keys.equitySymbols(), keys.optionInstrumentIds());
            return parseQuotes(result);
        } catch (Exception e) {
            log.warn("Robinhood live quotes unavailable for user {}: {}", ownerUserId, e.getMessage());
            return RobinhoodRhLiveQuotesDto.empty();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    RobinhoodRhLiveQuotesDto fetchWebullQuotes(QuoteKeys keys, List<RobinhoodAgenticPosition> positions) {
        if (!webullProps.serviceConfigured()
                || (keys.equitySymbols().isEmpty() && positions.isEmpty())) {
            return RobinhoodRhLiveQuotesDto.empty();
        }
        try {
            JsonNode result = webullQuoteClient.fetchQuotes(keys.equitySymbols(), positions);
            if (!result.path("ok").asBoolean(false)) {
                log.warn("Webull live quotes returned ok=false");
                return RobinhoodRhLiveQuotesDto.empty();
            }
            RobinhoodRhLiveQuotesDto parsed = parseQuotes(result);
            if (!parsed.equityPriceBySymbol().isEmpty() || !parsed.optionMarkPerShareByInstrumentId().isEmpty()) {
                log.debug(
                        "Webull live quotes: {} equities, {} options",
                        parsed.equityPriceBySymbol().size(),
                        parsed.optionMarkPerShareByInstrumentId().size());
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Webull live quotes unavailable: {}", e.getMessage());
            return RobinhoodRhLiveQuotesDto.empty();
        }
    }

    private static RobinhoodRhLiveQuotesDto mergeQuotes(
            RobinhoodRhLiveQuotesDto fallback, RobinhoodRhLiveQuotesDto preferred) {
        Map<String, BigDecimal> equity = new LinkedHashMap<>(fallback.equityPriceBySymbol());
        preferred.equityPriceBySymbol().forEach(equity::put);
        Map<String, BigDecimal> options = new LinkedHashMap<>(fallback.optionMarkPerShareByInstrumentId());
        preferred.optionMarkPerShareByInstrumentId().forEach(options::put);
        return new RobinhoodRhLiveQuotesDto(equity, options);
    }

    static Map<String, String> instrumentIdsByMatchKey(List<RobinhoodAgenticPosition> positions) {
        Map<String, String> out = new LinkedHashMap<>();
        if (positions == null) {
            return out;
        }
        for (RobinhoodAgenticPosition p : positions) {
            if (!"option".equalsIgnoreCase(p.getPositionType())) {
                continue;
            }
            String instrumentId = optionInstrumentId(p.getPositionKey());
            if (instrumentId == null) {
                continue;
            }
            out.put(
                    matchKey(p.getSymbol(), p.getQuantity(), p.getAverageBuyPrice()),
                    instrumentId);
        }
        return out;
    }

    static String matchKey(RobinhoodRhHoldingDto h) {
        return matchKey(h.symbol(), h.quantity(), h.averageBuyPrice());
    }

    static String matchKey(String symbol, BigDecimal quantity, BigDecimal averageBuyPrice) {
        String sym = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        String qty = quantity == null
                ? "0"
                : quantity.abs().stripTrailingZeros().toPlainString();
        String avg = averageBuyPrice == null
                ? "0"
                : averageBuyPrice.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return sym + "|" + qty + "|" + avg;
    }

    static String optionInstrumentId(String positionKey) {
        if (positionKey == null || positionKey.isBlank()) {
            return null;
        }
        int pipe = positionKey.indexOf('|');
        return pipe > 0 ? positionKey.substring(0, pipe) : positionKey;
    }

    private static void collectQuoteKeys(
            RobinhoodRhHoldingDto h,
            Set<String> equitySymbols,
            Map<String, String> instrumentIdByMatchKey,
            Set<String> optionInstrumentIds) {
        if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null && !h.symbol().isBlank()) {
            equitySymbols.add(h.symbol().trim().toUpperCase(Locale.ROOT));
            return;
        }
        if ("option".equalsIgnoreCase(h.positionType())) {
            String instrumentId = instrumentIdByMatchKey.get(matchKey(h));
            if (instrumentId != null) {
                optionInstrumentIds.add(instrumentId);
            }
        }
    }

    private static RobinhoodRhLiveQuotesDto parseQuotes(JsonNode result) {
        Map<String, BigDecimal> equity = parseEquityPriceMap(result.path("equity_prices"));
        Map<String, BigDecimal> options = parseOptionMarkMap(result.path("option_marks"));
        return new RobinhoodRhLiveQuotesDto(equity, options);
    }

    private static Map<String, BigDecimal> parseEquityPriceMap(JsonNode node) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return out;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            BigDecimal price = decimalOrNull(entry.getValue());
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                out.put(entry.getKey().trim().toUpperCase(Locale.ROOT), price);
            }
        }
        return out;
    }

    private static Map<String, BigDecimal> parseOptionMarkMap(JsonNode node) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return out;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            BigDecimal price = decimalOrNull(entry.getValue());
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                out.put(entry.getKey().trim(), price);
            }
        }
        return out;
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record QuoteKeys(List<String> equitySymbols, List<String> optionInstrumentIds) {}
}
