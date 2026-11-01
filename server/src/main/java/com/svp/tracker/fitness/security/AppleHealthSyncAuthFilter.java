package com.svp.tracker.fitness.security;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.security.TrackerUserPrincipal;
import com.svp.tracker.fitness.config.AppleHealthSyncProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts the Health Auto Export key on {@code POST /api/fitness/apple-health/sync}.
 * The key is not a JWT, so this path is skipped by {@code JwtAuthenticationFilter}.
 */
@Component
public class AppleHealthSyncAuthFilter extends OncePerRequestFilter {

    public static final String PATH = "/api/fitness/apple-health/sync";

    private final AppleHealthSyncProperties properties;
    private final AppUserRepository appUserRepository;

    public AppleHealthSyncAuthFilter(AppleHealthSyncProperties properties, AppUserRepository appUserRepository) {
        this.properties = properties;
        this.appUserRepository = appUserRepository;
    }

    public static boolean isSyncPost(HttpServletRequest request) {
        if (request == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return PATH.equals(uri) || (PATH + "/").equals(uri);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !isSyncPost(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, "{\"error\":\"not found\"}");
            return;
        }
        String presented = presentedKey(request);
        if (!sameSecret(properties.appleHealthSyncToken(), presented)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"error\":\"unauthorized\"}");
            return;
        }
        AppUser user = appUserRepository.findById(properties.appleHealthSyncUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"error\":\"unauthorized\"}");
            return;
        }
        TrackerUserPrincipal principal =
                new TrackerUserPrincipal(user.getId(), user.getUsername(), user.getRole(), user.isMarketsEnabled());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private static String presentedKey(HttpServletRequest request) {
        String key = firstHeader(request, "api-key");
        if (key.isBlank()) {
            key = firstHeader(request, "X-API-Key");
        }
        if (key.isBlank()) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                key = authorization.substring(7).trim();
            }
        }
        return key;
    }

    private static String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }

    private static boolean sameSecret(String expected, String given) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = given.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
