package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderRequestDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RobinhoodAgenticSidecarClient {

    private final RobinhoodAgenticProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RobinhoodAgenticSidecarClient(RobinhoodAgenticProperties props) {
        this.props = props;
    }

    public JsonNode sync(String accessToken, boolean syncAllAccounts) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("access_token", accessToken);
        body.put("sync_all", syncAllAccounts);
        return post("/v1/sync", body);
    }

    public JsonNode refreshToken(String refreshToken) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("refresh_token", refreshToken);
        return post("/v1/refresh-token", body);
    }

    public JsonNode reviewOrder(String accessToken, RobinhoodAgenticOrderRequestDto order, String accountNumber) {
        requireConfigured();
        return post("/v1/review-order", orderBody(accessToken, order, accountNumber));
    }

    public JsonNode placeOrder(String accessToken, RobinhoodAgenticOrderRequestDto order, String accountNumber) {
        requireConfigured();
        return post("/v1/place-order", orderBody(accessToken, order, accountNumber));
    }

    private ObjectNode orderBody(String accessToken, RobinhoodAgenticOrderRequestDto order, String accountNumber) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("access_token", accessToken);
        if (accountNumber != null && !accountNumber.isBlank()) {
            body.put("account_number", accountNumber);
        }
        body.put("symbol", order.symbol().trim().toUpperCase());
        body.put("side", order.side().trim().toLowerCase());
        body.put("type", order.type() == null || order.type().isBlank() ? "market" : order.type().trim().toLowerCase());
        putDecimal(body, "quantity", order.quantity());
        putDecimal(body, "amount", order.amount());
        putDecimal(body, "limit_price", order.limitPrice());
        if (order.timeInForce() != null && !order.timeInForce().isBlank()) {
            body.put("time_in_force", order.timeInForce().trim().toLowerCase());
        }
        return body;
    }

    private static void putDecimal(ObjectNode body, String field, BigDecimal value) {
        if (value != null) {
            body.put(field, value.stripTrailingZeros().toPlainString());
        }
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            byte[] bodyBytes = objectMapper.writeValueAsBytes(body);
            URI uri = URI.create(stripTrailingSlash(props.serviceBaseUrl()) + path);
            SidecarResponse response = postJson(uri, bodyBytes, props.serviceTimeoutMs());
            if (response.status() / 100 != 2) {
                if (response.status() == 401) {
                    throw new RobinhoodAgenticUnauthorizedException(response.body());
                }
                throw new IllegalStateException(
                        "Robinhood Agentic sidecar failed (HTTP " + response.status() + "): " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (RobinhoodAgenticUnauthorizedException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            throw new IllegalStateException(
                    "Robinhood Agentic sidecar unreachable at " + props.serviceBaseUrl() + ": " + detail, e);
        }
    }

    private void requireConfigured() {
        if (!props.serviceConfigured()) {
            throw new IllegalStateException("Robinhood Agentic sidecar is not configured");
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

    private record SidecarResponse(int status, String body) {}
}
