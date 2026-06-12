package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    public JsonNode sync(String accessToken, boolean syncDefaultAccount) {
        if (!props.serviceConfigured()) {
            throw new IllegalStateException("Robinhood Agentic sidecar is not configured");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("access_token", accessToken);
            body.put("sync_default", syncDefaultAccount);
            byte[] bodyBytes = objectMapper.writeValueAsBytes(body);
            URI uri = URI.create(stripTrailingSlash(props.serviceBaseUrl()) + "/v1/sync");
            String responseBody = postJson(uri, bodyBytes, props.serviceTimeoutMs());
            return objectMapper.readTree(responseBody);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            throw new IllegalStateException(
                    "Robinhood Agentic sidecar unreachable at "
                            + props.serviceBaseUrl()
                            + ": "
                            + detail,
                    e);
        }
    }

    /**
     * POST JSON via {@link HttpURLConnection} — JDK {@code HttpClient} sends {@code Expect: 100-continue},
     * which uvicorn/FastAPI can mishandle (empty body → HTTP 422).
     */
    private static String postJson(URI uri, byte[] bodyBytes, int timeoutMs) throws IOException {
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
        if (status / 100 != 2) {
            throw new IllegalStateException(
                    "Robinhood Agentic sync failed (HTTP " + status + "): " + responseBody);
        }
        return responseBody;
    }

    private static String stripTrailingSlash(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
