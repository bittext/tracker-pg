package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.RobinhoodAgenticBankingProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderRequestDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RobinhoodAgenticSidecarClient {

    private final RobinhoodAgenticProperties props;
    private final RobinhoodAgenticBankingProperties bankingProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RobinhoodAgenticSidecarClient(
            RobinhoodAgenticProperties props, RobinhoodAgenticBankingProperties bankingProps) {
        this.props = props;
        this.bankingProps = bankingProps;
    }

    public JsonNode sync(String accessToken, boolean syncAllAccounts) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("access_token", accessToken);
        body.put("sync_all", syncAllAccounts);
        return post("/v1/sync", body);
    }

    public JsonNode cryptoSync(String apiKey, String privateKeyBase64) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("api_key", apiKey);
        body.put("private_key_base64", privateKeyBase64);
        return post("/v1/crypto/sync", body);
    }

    public JsonNode cryptoPlaceOrder(
            String apiKey,
            String privateKeyBase64,
            String accountNumber,
            String symbol,
            String side,
            BigDecimal assetQuantity,
            BigDecimal quoteAmount,
            String clientOrderId) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("api_key", apiKey);
        body.put("private_key_base64", privateKeyBase64);
        body.put("account_number", accountNumber);
        body.put("symbol", symbol.trim().toUpperCase());
        body.put("side", side.trim().toLowerCase());
        putDecimal(body, "asset_quantity", assetQuantity);
        putDecimal(body, "quote_amount", quoteAmount);
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            body.put("client_order_id", clientOrderId);
        }
        return post("/v1/crypto/place-order", body);
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

    public JsonNode fetchFinancials(String accessToken, String symbol, int limit) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("access_token", accessToken);
        body.put("symbol", symbol == null ? "" : symbol.trim().toUpperCase());
        body.put("limit", Math.max(1, Math.min(limit, 40)));
        return post("/v1/financials", body);
    }

    public JsonNode fetchQuotes(String accessToken, List<String> symbols, List<String> optionInstrumentIds) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("access_token", accessToken);
        var equity = body.putArray("symbols");
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank()) {
                equity.add(symbol.trim().toUpperCase());
            }
        }
        var options = body.putArray("option_instrument_ids");
        for (String id : optionInstrumentIds) {
            if (id != null && !id.isBlank()) {
                options.add(id.trim());
            }
        }
        return post("/v1/quotes", body);
    }

    public JsonNode bankingSync(String accessToken, int transactionLimit) {
        requireBankingConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("access_token", accessToken);
        body.put("transaction_limit", Math.max(1, Math.min(transactionLimit, 50)));
        return bankingPost("/v1/banking/sync", body);
    }

    public JsonNode bankingRefreshToken(String refreshToken) {
        requireBankingConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("refresh_token", refreshToken);
        return bankingPost("/v1/banking/refresh-token", body);
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
        return postToBase(stripTrailingSlash(props.serviceBaseUrl()), props.serviceTimeoutMs(), path, body);
    }

    private JsonNode bankingPost(String path, ObjectNode body) {
        return postToBase(
                stripTrailingSlash(bankingProps.serviceBaseUrl()), bankingProps.serviceTimeoutMs(), path, body);
    }

    private JsonNode postToBase(String baseUrl, int timeoutMs, String path, ObjectNode body) {
        try {
            byte[] bodyBytes = objectMapper.writeValueAsBytes(body);
            URI uri = URI.create(baseUrl + path);
            SidecarResponse response = postJson(uri, bodyBytes, timeoutMs);
            if (response.status() / 100 != 2) {
                if (response.status() == 401) {
                    throw new RobinhoodAgenticUnauthorizedException(extractErrorMessage(response.body()));
                }
                if (response.status() == 400) {
                    throw new IllegalArgumentException(extractErrorMessage(response.body()));
                }
                throw new IllegalStateException(
                        "Robinhood Agentic sidecar failed (HTTP " + response.status() + "): " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (RobinhoodAgenticUnauthorizedException | IllegalStateException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            throw new IllegalStateException("Robinhood Agentic sidecar unreachable at " + baseUrl + ": " + detail, e);
        }
    }

    private void requireBankingConfigured() {
        if (!bankingProps.serviceConfigured()) {
            throw new IllegalStateException("Robinhood Agentic Banking sidecar is not configured");
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

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Unauthorized";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode detail = node.get("detail");
            if (detail != null && !detail.isNull()) {
                if (detail.isTextual()) {
                    return detail.asText();
                }
                return detail.toString();
            }
        } catch (Exception ignored) {
            // fall through to raw body
        }
        return body.length() <= 500 ? body : body.substring(0, 497) + "...";
    }

    private record SidecarResponse(int status, String body) {}
}
