package com.svp.tracker.finance.predicts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary {@link SentimentScorer} that calls the {@code finbert-svc} Python sidecar (ProsusAI/finbert).
 * Texts are batched up to {@code finbert.max-batch-size}; on any error (connection refused, 5xx, timeout)
 * we transparently fall through to {@link HeuristicSentimentScorer} so ingestion never blocks waiting for
 * the model container. The sidecar returns probabilities; we persist the argmax label and the polarity
 * {@code positive − negative} as {@code score} in {@code [-1, +1]}.
 */
@Component
@Primary
@Slf4j
public class FinbertSentimentClient implements SentimentScorer {

    private final FinancePredictsProperties props;
    private final HeuristicSentimentScorer heuristic;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FinbertSentimentClient(FinancePredictsProperties props, HeuristicSentimentScorer heuristic) {
        this.props = props;
        this.heuristic = heuristic;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public List<SentimentScore> score(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!props.finbert().enabled()) {
            return heuristic.score(texts);
        }
        int batch = Math.max(1, props.finbert().maxBatchSize());
        List<SentimentScore> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batch) {
            List<String> chunk = texts.subList(i, Math.min(texts.size(), i + batch));
            List<SentimentScore> chunkScored = scoreChunk(chunk);
            out.addAll(chunkScored);
        }
        return out;
    }

    private List<SentimentScore> scoreChunk(List<String> chunk) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode arr = body.putArray("texts");
            for (String t : chunk) {
                arr.add(t == null ? "" : t);
            }
            URI uri = URI.create(stripTrailingSlash(props.finbert().baseUrl()) + "/score");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(500, props.finbert().timeoutMs())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn(
                        "FinBERT sidecar returned status {} (body chars={}); using heuristic fallback",
                        response.statusCode(),
                        response.body() == null ? 0 : response.body().length());
                return heuristic.score(chunk);
            }
            return parseResponse(response.body(), chunk.size());
        } catch (Exception e) {
            log.warn("FinBERT sidecar call failed ({}); using heuristic fallback", e.getMessage());
            return heuristic.score(chunk);
        }
    }

    private List<SentimentScore> parseResponse(String body, int expected) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode scores = root.get("scores");
            if (scores == null || !scores.isArray()) {
                return List.of();
            }
            List<SentimentScore> out = new ArrayList<>(scores.size());
            for (JsonNode node : scores) {
                String label = textOr(node, "label", "neutral");
                BigDecimal score = decimalOr(node, "score");
                BigDecimal confidence = decimalOr(node, "confidence");
                out.add(new SentimentScore(label.toLowerCase(java.util.Locale.ROOT), score, confidence));
            }
            // Be defensive: if the sidecar returned a different cardinality, fall back to heuristic.
            if (out.size() != expected) {
                log.warn(
                        "FinBERT response cardinality mismatch (got={}, expected={}); using heuristic fallback",
                        out.size(),
                        expected);
                return List.of();
            }
            return out;
        } catch (Exception e) {
            log.warn("FinBERT response parse failed ({}); using heuristic fallback", e.getMessage());
            return List.of();
        }
    }

    private static String textOr(JsonNode node, String field, String def) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? def : child.asText(def);
    }

    private static BigDecimal decimalOr(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return BigDecimal.valueOf(child.asDouble(0.0)).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://finbert:8000";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
