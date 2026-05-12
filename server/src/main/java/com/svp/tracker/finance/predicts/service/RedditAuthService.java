package com.svp.tracker.finance.predicts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caches a Reddit OAuth2 bearer token (grant_type=client_credentials). Reddit rotates tokens every
 * hour by default, so we refresh proactively at 80% of the advertised TTL. {@link #currentBearer()}
 * is thread-safe and lazily acquires the first token; callers can rely on it never returning a stale
 * token because the refresh is double-checked under {@code synchronized(this)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedditAuthService {

    private static final URI TOKEN_URI = URI.create("https://www.reddit.com/api/v1/access_token");
    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(15);
    /** Refresh slightly before expiry so concurrent callers don't race against revocation. */
    private static final double REFRESH_AT_FRACTION = 0.8;

    private final FinancePredictsProperties props;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedBearer;
    private volatile Instant refreshAt = Instant.EPOCH;

    /** Returns null when Reddit is disabled or credentials are missing. */
    public synchronized String currentBearer() {
        FinancePredictsProperties.Reddit reddit = props.reddit();
        if (!reddit.enabled()
                || reddit.clientId() == null
                || reddit.clientId().isBlank()
                || reddit.clientSecret() == null
                || reddit.clientSecret().isBlank()) {
            return null;
        }
        if (cachedBearer != null && Instant.now().isBefore(refreshAt)) {
            return cachedBearer;
        }
        return refresh();
    }

    private String refresh() {
        FinancePredictsProperties.Reddit reddit = props.reddit();
        String basic = Base64.getEncoder()
                .encodeToString((reddit.clientId() + ":" + reddit.clientSecret()).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=client_credentials";
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(TOKEN_TIMEOUT)
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", reddit.userAgent())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn(
                        "Reddit token request failed: status={} body chars={}",
                        response.statusCode(),
                        response.body() == null ? 0 : response.body().length());
                cachedBearer = null;
                refreshAt = Instant.EPOCH;
                return null;
            }
            JsonNode node = objectMapper.readTree(response.body());
            String token = node.path("access_token").asText(null);
            int expiresInSec = node.path("expires_in").asInt(3600);
            if (token == null || token.isBlank()) {
                cachedBearer = null;
                refreshAt = Instant.EPOCH;
                return null;
            }
            cachedBearer = token;
            refreshAt = Instant.now().plusSeconds((long) (expiresInSec * REFRESH_AT_FRACTION));
            log.info("Reddit bearer token refreshed; valid for ~{}s (refresh at {})", expiresInSec, refreshAt);
            return token;
        } catch (Exception e) {
            log.warn("Reddit token request error: {}", e.getMessage());
            cachedBearer = null;
            refreshAt = Instant.EPOCH;
            return null;
        }
    }
}
