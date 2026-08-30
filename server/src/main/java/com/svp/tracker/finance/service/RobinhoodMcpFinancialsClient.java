package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calls Robinhood Agentic MCP {@code get_financials} directly when the sidecar route is missing
 * or empty. Same trading MCP endpoint as the sidecar.
 */
@Component
@Slf4j
public class RobinhoodMcpFinancialsClient {

    static final String MCP_ENDPOINT = "https://agent.robinhood.com/mcp/trading";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<RobinhoodFinancialsService.FinancialsRow> quarterlyFinancials(String accessToken, String symbol, int limit) {
        if (accessToken == null || accessToken.isBlank() || symbol == null || symbol.isBlank()) {
            return List.of();
        }
        String sessionId = null;
        try {
            McpCall init = post(accessToken, null, rpc("initialize", initParams()), false);
            sessionId = init.sessionId();
            if (init.status() == 401) {
                throw new RobinhoodAgenticUnauthorizedException("MCP initialize returned 401");
            }
            if (init.status() < 200 || init.status() >= 300 || init.body() == null) {
                log.warn("MCP initialize HTTP {} for get_financials {}", init.status(), symbol);
                return List.of();
            }
            post(accessToken, sessionId, notify("notifications/initialized"), true);
            McpCall call = post(
                    accessToken,
                    sessionId,
                    rpc("tools/call", toolCallParams(symbol, limit)),
                    false);
            if (call.status() == 401) {
                throw new RobinhoodAgenticUnauthorizedException("MCP get_financials returned 401");
            }
            if (call.status() < 200 || call.status() >= 300 || call.body() == null) {
                log.warn("MCP get_financials HTTP {} for {}", call.status(), symbol);
                return List.of();
            }
            JsonNode rpc = call.body();
            if (rpc.has("error")) {
                log.warn("MCP get_financials error for {}: {}", symbol, rpc.path("error"));
                return List.of();
            }
            return parseRows(rpc.path("result"), symbol);
        } catch (RobinhoodAgenticUnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("MCP get_financials failed for {}: {}", symbol, e.toString());
            return List.of();
        } finally {
            closeSession(accessToken, sessionId);
        }
    }

    static List<RobinhoodFinancialsService.FinancialsRow> parseRows(JsonNode toolResult, String symbol) {
        JsonNode payload = toolPayload(toolResult);
        JsonNode data = payload.path("data").isObject() ? payload.path("data") : payload;
        JsonNode results = data.path("results");
        if (!results.isArray()) {
            return List.of();
        }
        String wanted = symbol == null ? "" : symbol.trim().toUpperCase();
        List<RobinhoodFinancialsService.FinancialsRow> out = new ArrayList<>();
        for (JsonNode entry : results) {
            if (entry == null || !entry.isObject()) {
                continue;
            }
            String entrySymbol = entry.path("symbol").asText("").trim().toUpperCase();
            if (!wanted.isBlank() && !entrySymbol.isBlank() && !wanted.equals(entrySymbol)) {
                continue;
            }
            JsonNode rows = entry.path("financials");
            if (!rows.isArray()) {
                continue;
            }
            for (JsonNode n : rows) {
                String periodEnd = text(n, "period_end_date");
                if (periodEnd.isBlank()) {
                    continue;
                }
                out.add(new RobinhoodFinancialsService.FinancialsRow(
                        n.path("fiscal_year").asInt(0),
                        n.path("fiscal_quarter").asInt(0),
                        periodEnd,
                        parseNum(text(n, "revenue")),
                        parseNum(text(n, "gross_profit")),
                        parseNum(text(n, "net_income")),
                        parseNum(text(n, "net_margin"))));
            }
            break;
        }
        return out;
    }

    static JsonNode toolPayload(JsonNode result) {
        if (result != null && result.has("structuredContent") && !result.path("structuredContent").isNull()) {
            return result.path("structuredContent");
        }
        JsonNode content = result == null ? null : result.path("content");
        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                if (!"text".equals(block.path("type").asText(""))) {
                    continue;
                }
                String text = block.path("text").asText("").trim();
                JsonNode parsed = leadingJsonObject(text);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return result == null ? new ObjectMapper().createObjectNode() : result;
    }

    static JsonNode leadingJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(text);
        } catch (Exception ignored) {
            // Fall through to first object in mixed prose.
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return mapper.readTree(text.substring(start, i + 1));
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    static JsonNode parseSseOrJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        ObjectMapper mapper = new ObjectMapper();
        if (trimmed.startsWith("{")) {
            try {
                return mapper.readTree(trimmed);
            } catch (Exception ignored) {
                // Try SSE frames below.
            }
        }
        JsonNode last = null;
        for (String line : raw.split("\\R")) {
            String t = line.trim();
            if (!t.startsWith("data:")) {
                continue;
            }
            String payload = t.substring(5).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                continue;
            }
            try {
                last = mapper.readTree(payload);
            } catch (Exception ignored) {
                // Skip heartbeat / non-JSON frames.
            }
        }
        return last;
    }

    private ObjectNode initParams() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode client = objectMapper.createObjectNode();
        client.put("name", "tracker-pg-financials");
        client.put("version", "1.0");
        params.set("clientInfo", client);
        return params;
    }

    private ObjectNode toolCallParams(String symbol, int limit) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "get_financials");
        ObjectNode args = objectMapper.createObjectNode();
        args.putArray("symbols").add(symbol.trim().toUpperCase());
        args.put("period", "quarterly");
        args.put("limit", Math.max(1, Math.min(limit, 40)));
        params.set("arguments", args);
        return params;
    }

    private ObjectNode rpc(String method, ObjectNode params) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", method.hashCode() & 0x7fff);
        body.put("method", method);
        if (params != null) {
            body.set("params", params);
        }
        return body;
    }

    private ObjectNode notify(String method) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        body.set("params", objectMapper.createObjectNode());
        return body;
    }

    private McpCall post(String accessToken, String sessionId, ObjectNode body, boolean notification)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(MCP_ENDPOINT))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (sessionId != null && !sessionId.isBlank()) {
            b.header("Mcp-Session-Id", sessionId);
        }
        HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String newSession = resp.headers().firstValue("Mcp-Session-Id").orElse(sessionId);
        if (notification) {
            return new McpCall(resp.statusCode(), newSession, null);
        }
        return new McpCall(resp.statusCode(), newSession, parseSseOrJson(resp.body()));
    }

    private void closeSession(String accessToken, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(MCP_ENDPOINT))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Mcp-Session-Id", sessionId)
                    .DELETE()
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Session cleanup is best-effort.
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.isObject()) {
            return "";
        }
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static Double parseNum(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record McpCall(int status, String sessionId, JsonNode body) {}
}
