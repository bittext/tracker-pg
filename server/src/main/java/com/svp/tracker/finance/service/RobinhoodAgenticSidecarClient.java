package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RobinhoodAgenticSidecarClient {

    private final RobinhoodAgenticProperties props;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RobinhoodAgenticSidecarClient(RobinhoodAgenticProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public JsonNode sync(String accessToken, boolean syncDefaultAccount) {
        if (!props.serviceConfigured()) {
            throw new IllegalStateException("Robinhood Agentic sidecar is not configured");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("access_token", accessToken);
            body.put("sync_default", syncDefaultAccount);
            URI uri = URI.create(stripTrailingSlash(props.serviceBaseUrl()) + "/v1/sync");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(props.serviceTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String detail = response.body() == null ? "" : response.body();
                throw new IllegalStateException(
                        "Robinhood Agentic sync failed (HTTP " + response.statusCode() + "): " + detail);
            }
            return objectMapper.readTree(response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Robinhood Agentic sidecar unreachable: " + e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
