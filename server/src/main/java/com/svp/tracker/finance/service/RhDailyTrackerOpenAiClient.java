package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Thin OpenAI Chat Completions client for Daily Tracker AI insights. */
@Component
@Slf4j
public class RhDailyTrackerOpenAiClient {

    private final RobinhoodRhDailyTrackerProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public RhDailyTrackerOpenAiClient(RobinhoodRhDailyTrackerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String completeJson(String systemPrompt, String userPrompt) {
        var ai = props.ai();
        if (!ai.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Daily Tracker AI is not configured. Set TRACKER_FINANCE_RH_DAILY_TRACKER_AI_ENABLED=true and TRACKER_FINANCE_RH_DAILY_TRACKER_AI_API_KEY.");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", ai.model());
            body.put("temperature", 0.4);
            body.put("max_tokens", ai.maxOutputTokens());
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_object");
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            String url = ai.baseUrl() + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(ai.timeoutMs()))
                    .header("Authorization", "Bearer " + ai.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI chat completions failed status={} bodyLen={}", response.statusCode(), response.body() == null ? 0 : response.body().length());
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "OpenAI request failed (HTTP " + response.statusCode() + ")");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText("").isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned an empty completion");
            }
            return content.asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request interrupted");
        } catch (Exception e) {
            log.warn("OpenAI chat completions error: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed: " + e.getMessage());
        }
    }
}
