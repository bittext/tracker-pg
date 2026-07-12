package com.svp.tracker.auth.service;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.dto.StepUpTokenDto;
import com.svp.tracker.auth.repository.AppUserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Short-lived step-up tokens for sensitive Markets writes (orders, credentials, auto-trade).
 * Tokens are held in memory; restart invalidates outstanding step-ups (acceptable for v11).
 */
@Service
@RequiredArgsConstructor
public class StepUpAuthService {

    public static final String HEADER = "X-Step-Up-Token";
    private static final long TTL_SECONDS = 300;

    private final AppUserRepository appUserRepository;
    private final PasswordHashService passwordHashService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public StepUpTokenDto issue(long userId, String rawPassword) {
        AppUser user = appUserRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
        if (!passwordHashService.verify(rawPassword, user.getPasswordHash(), user.getPasswordSalt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password verification failed.");
        }
        purgeExpired();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant exp = Instant.now().plusSeconds(TTL_SECONDS);
        tokens.put(token, new Entry(userId, exp));
        return new StepUpTokenDto(token, exp);
    }

    public boolean isValid(long userId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        purgeExpired();
        Entry e = tokens.get(token.trim());
        return e != null && e.userId() == userId && e.expiresAt().isAfter(Instant.now());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
    }

    private record Entry(long userId, Instant expiresAt) {}
}
