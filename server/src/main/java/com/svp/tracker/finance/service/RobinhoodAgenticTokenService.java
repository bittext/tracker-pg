package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticTokenService {

    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticTokenCrypto tokenCrypto;

    /** Decrypt access token; refresh and persist when sidecar returns 401. */
    @Transactional
    public String requireAccessToken(RobinhoodAgenticConnection conn) {
        String accessToken = tokenCrypto.open(conn.getAccessToken());
        return accessToken;
    }

    @Transactional
    public void refreshAndSave(RobinhoodAgenticConnection conn) {
        String refresh = conn.getRefreshToken();
        if (refresh == null || refresh.isBlank()) {
            throw new IllegalStateException("No refresh_token stored — re-paste .tokens.json from phase0_oauth.py");
        }
        String plainRefresh = tokenCrypto.open(refresh);
        JsonNode result = sidecarClient.refreshToken(plainRefresh);
        if (!result.path("ok").asBoolean(true) && result.get("access_token") == null) {
            throw new IllegalStateException("Token refresh failed");
        }
        String newAccess = result.path("access_token").asText(null);
        if (newAccess == null || newAccess.isBlank()) {
            throw new IllegalStateException("Token refresh returned no access_token");
        }
        conn.setAccessToken(tokenCrypto.seal(newAccess));
        String newRefresh = result.path("refresh_token").asText(null);
        if (newRefresh != null && !newRefresh.isBlank()) {
            conn.setRefreshToken(tokenCrypto.seal(newRefresh));
        }
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);
        log.info("Robinhood Agentic tokens refreshed for user {}", conn.getOwnerUserId());
    }

    @FunctionalInterface
    public interface SidecarCall<T> {
        T call(String accessToken);
    }

    /** Run a sidecar call; on 401 refresh tokens once and retry. */
    @Transactional
    public <T> T withFreshToken(RobinhoodAgenticConnection conn, SidecarCall<T> call) {
        String accessToken = requireAccessToken(conn);
        try {
            return call.call(accessToken);
        } catch (RobinhoodAgenticUnauthorizedException e) {
            log.info("Robinhood Agentic access token expired for user {}, refreshing", conn.getOwnerUserId());
            refreshAndSave(conn);
            return call.call(requireAccessToken(conn));
        }
    }

    /**
     * Sidecar sync HTTP only — runs outside any caller transaction so network failures do not mark
     * the caller rollback-only before errors are handled.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JsonNode syncAllAccounts(RobinhoodAgenticConnection conn) {
        return withFreshToken(conn, token -> sidecarClient.sync(token, true));
    }

    /** Sidecar quote HTTP only — same isolation as {@link #syncAllAccounts}. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JsonNode fetchHoldingsQuotes(
            RobinhoodAgenticConnection conn, List<String> symbols, List<String> optionInstrumentIds) {
        return withFreshToken(
                conn, token -> sidecarClient.fetchQuotes(token, symbols, optionInstrumentIds));
    }

    /** Sidecar get_financials HTTP only — same isolation as {@link #syncAllAccounts}. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JsonNode fetchFinancials(RobinhoodAgenticConnection conn, String symbol, int limit) {
        return withFreshToken(conn, token -> sidecarClient.fetchFinancials(token, symbol, limit));
    }
}
