package com.svp.tracker.markets.security;

import com.svp.tracker.auth.security.TrackerUserPrincipal;
import com.svp.tracker.auth.service.StepUpAuthService;
import com.svp.tracker.markets.service.MarketsAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates Markets/trading APIs behind {@code marketsEnabled} (or ADMIN) and requires step-up on
 * sensitive writes. Also audits mutating Markets calls.
 */
@Component
public class MarketsAccessFilter extends OncePerRequestFilter {

    private final StepUpAuthService stepUpAuthService;
    private final MarketsAuditService marketsAuditService;

    public MarketsAccessFilter(StepUpAuthService stepUpAuthService, MarketsAuditService marketsAuditService) {
        this.stepUpAuthService = stepUpAuthService;
        this.marketsAuditService = marketsAuditService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = normalizedPath(request);
        return !(isMarketsPath(path) || isPredictsPath(path));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TrackerUserPrincipal principal)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"error\":\"unauthorized\"}");
            return;
        }
        if (!principal.canAccessMarkets()) {
            writeJson(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"markets_disabled\",\"message\":\"Markets access is not enabled for this account.\"}");
            return;
        }

        String path = normalizedPath(request);
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        boolean mutating = !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);

        if (mutating && requiresStepUp(path, method)) {
            String stepUp = request.getHeader(StepUpAuthService.HEADER);
            if (!stepUpAuthService.isValid(principal.id(), stepUp)) {
                writeJson(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "{\"error\":\"step_up_required\",\"message\":\"Re-enter your password to confirm this Markets action.\"}");
                return;
            }
        }

        if (mutating) {
            try {
                marketsAuditService.record(
                        principal, method + " " + path, method, path, request.getRemoteAddr());
            } catch (Exception ignored) {
                /* audit must not block trading */
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isMarketsPath(String path) {
        return path.startsWith("/api/markets")
                || path.startsWith("/api/finance/robinhood")
                || path.startsWith("/api/finance/credit/agentic");
    }

    private static boolean isPredictsPath(String path) {
        return path.startsWith("/api/finance/predicts");
    }

    private static boolean requiresStepUp(String path, String method) {
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/sync")) {
            return false;
        }
        if (lower.contains("/agentic/") || lower.contains("/crypto-trading/")) {
            if (lower.contains("/tokens")
                    || lower.contains("/connection")
                    || lower.contains("/credentials")
                    || lower.contains("/orders")
                    || lower.contains("/settings")
                    || lower.contains("/auto-trade")) {
                return true;
            }
        }
        return lower.contains("/import-csv");
    }

    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
