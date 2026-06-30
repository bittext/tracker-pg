package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.WebullQuoteProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WebullQuoteSidecarClient {

    private final WebullQuoteProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebullQuoteSidecarClient(WebullQuoteProperties props) {
        this.props = props;
    }

    public JsonNode fetchQuotes(List<String> symbols, List<RobinhoodAgenticPosition> optionPositions) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode equity = body.putArray("symbols");
        for (String symbol : normalizeSymbols(symbols)) {
            equity.add(symbol);
        }
        ArrayNode options = body.putArray("options");
        for (RobinhoodAgenticPosition position : optionPositions) {
            if (position == null || !"option".equalsIgnoreCase(position.getPositionType())) {
                continue;
            }
            String instrumentId = RobinhoodRhHoldingQuoteService.optionInstrumentId(position.getPositionKey());
            if (instrumentId == null
                    || position.getSymbol() == null
                    || position.getSymbol().isBlank()
                    || position.getStrikePrice() == null
                    || position.getExpirationDate() == null
                    || position.getOptionType() == null
                    || position.getOptionType().isBlank()) {
                continue;
            }
            ObjectNode row = options.addObject();
            row.put("instrument_id", instrumentId);
            row.put("symbol", position.getSymbol().trim().toUpperCase(Locale.ROOT));
            row.put("strike", position.getStrikePrice().stripTrailingZeros().toPlainString());
            row.put("expiration", position.getExpirationDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            row.put("option_type", position.getOptionType().trim().toLowerCase(Locale.ROOT));
        }
        return post("/v1/quotes", body);
    }

    private static List<String> normalizeSymbols(List<String> symbols) {
        Set<String> out = new LinkedHashSet<>();
        if (symbols != null) {
            for (String symbol : symbols) {
                if (symbol != null && !symbol.isBlank()) {
                    out.add(symbol.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(out);
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            byte[] bodyBytes = objectMapper.writeValueAsBytes(body);
            URI uri = URI.create(stripTrailingSlash(props.serviceBaseUrl()) + path);
            int status;
            String responseBody;
            try {
                SidecarResponse response = postJson(uri, bodyBytes, props.serviceTimeoutMs());
                status = response.status();
                responseBody = response.body();
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Webull quote sidecar unreachable at " + props.serviceBaseUrl() + ": " + rootMessage(e), e);
            }
            if (status / 100 != 2) {
                throw new IllegalStateException(
                        "Webull quote sidecar failed (HTTP " + status + "): " + responseBody);
            }
            return objectMapper.readTree(responseBody);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Webull quote sidecar unreachable at " + props.serviceBaseUrl() + ": " + rootMessage(e), e);
        }
    }

    private void requireConfigured() {
        if (!props.serviceConfigured()) {
            throw new IllegalStateException("Webull quote sidecar is not configured");
        }
    }

    private static SidecarResponse postJson(URI uri, byte[] bodyBytes, int timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        int connectTimeout = Math.min(timeoutMs, 30_000);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setFixedLengthStreamingMode(bodyBytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }
        int status = conn.getResponseCode();
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = "";
        if (stream != null) {
            responseBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new SidecarResponse(status, responseBody);
    }

    private static String stripTrailingSlash(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private record SidecarResponse(int status, String body) {}
}
