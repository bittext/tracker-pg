package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.svp.tracker.finance.domain.RobinhoodAgenticBankingConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticBankingConnectionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticBankingTokenService {

    private final RobinhoodAgenticBankingConnectionRepository connectionRepository;
    private final RobinhoodAgenticSidecarClient sidecarClient;
    private final RobinhoodAgenticTokenCrypto tokenCrypto;

    @Transactional
    public String requireAccessToken(RobinhoodAgenticBankingConnection conn) {
        return tokenCrypto.open(conn.getAccessToken());
    }

    @Transactional
    public void refreshAndSave(RobinhoodAgenticBankingConnection conn) {
        String refresh = conn.getRefreshToken();
        if (refresh == null || refresh.isBlank()) {
            throw new IllegalStateException(
                    "No refresh_token stored — re-paste .tokens-banking.json from phase0_oauth.py --banking");
        }
        String plainRefresh = tokenCrypto.open(refresh);
        JsonNode result = sidecarClient.bankingRefreshToken(plainRefresh);
        if (!result.path("ok").asBoolean(true) && result.get("access_token") == null) {
            throw new IllegalStateException("Banking token refresh failed");
        }
        String newAccess = result.path("access_token").asText(null);
        if (newAccess == null || newAccess.isBlank()) {
            throw new IllegalStateException("Banking token refresh returned no access_token");
        }
        conn.setAccessToken(tokenCrypto.seal(newAccess));
        String newRefresh = result.path("refresh_token").asText(null);
        if (newRefresh != null && !newRefresh.isBlank()) {
            conn.setRefreshToken(tokenCrypto.seal(newRefresh));
        }
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);
        log.info("Robinhood Agentic Banking tokens refreshed for user {}", conn.getOwnerUserId());
    }

    @FunctionalInterface
    public interface SidecarCall<T> {
        T call(String accessToken);
    }

    @Transactional
    public <T> T withFreshToken(RobinhoodAgenticBankingConnection conn, SidecarCall<T> call) {
        String accessToken = requireAccessToken(conn);
        try {
            return call.call(accessToken);
        } catch (RobinhoodAgenticUnauthorizedException e) {
            log.info(
                    "Robinhood Agentic Banking access token expired for user {}, refreshing",
                    conn.getOwnerUserId());
            refreshAndSave(conn);
            return call.call(requireAccessToken(conn));
        }
    }
}
