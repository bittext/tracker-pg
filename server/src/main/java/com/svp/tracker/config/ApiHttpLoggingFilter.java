package com.svp.tracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs every {@code /api/**} request/response (except CORS OPTIONS): servlet fields, non-sensitive headers, and
 * bodies. Bodies for {@code /api/admin/logs} are not logged verbatim (avoids filling the in-memory log buffer with
 * duplicate log text). Omits Authorization/Cookie headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class ApiHttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiHttpLoggingFilter.class);

    private static final int MAX_BODY_CHARS = 8192;
    private static final int MAX_HEADER_VALUE_CHARS = 512;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String m = request.getMethod();
        if ("OPTIONS".equals(m)) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper req =
                request instanceof ContentCachingRequestWrapper w ? w : new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        long t0 = System.nanoTime();
        try {
            filterChain.doFilter(req, res);
        } finally {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            byte[] reqBody = req.getContentAsByteArray();
            byte[] resBody = res.getContentAsByteArray();

            String uri = req.getRequestURI();
            log.info(
                    "{} {} | {}ms\nrequest object: {}\nrequest body: {}\nresponse object: {}\nresponse body: {}",
                    req.getMethod(),
                    uri,
                    ms,
                    describeRequest(req),
                    preview(reqBody, uri, true),
                    describeResponse(res),
                    preview(resBody, uri, false));
            res.copyBodyToResponse();
        }
    }

    /** Printable summary matching HttpServletRequest state (not Java toString()). */
    private static String describeRequest(HttpServletRequest r) {
        return "HttpServletRequest{"
                + "method="
                + r.getMethod()
                + ", requestURI="
                + r.getRequestURI()
                + ", requestURL="
                + r.getRequestURL()
                + ", queryString="
                + dashIfEmpty(r.getQueryString())
                + ", protocol="
                + r.getProtocol()
                + ", scheme="
                + r.getScheme()
                + ", serverName="
                + r.getServerName()
                + ", serverPort="
                + r.getServerPort()
                + ", contextPath="
                + dashIfEmpty(r.getContextPath())
                + ", servletPath="
                + dashIfEmpty(r.getServletPath())
                + ", pathInfo="
                + dashIfEmpty(r.getPathInfo())
                + ", remoteAddr="
                + r.getRemoteAddr()
                + ", remoteHost="
                + r.getRemoteHost()
                + ", remotePort="
                + r.getRemotePort()
                + ", contentType="
                + dashIfEmpty(r.getContentType())
                + ", contentLength="
                + r.getContentLengthLong()
                + ", characterEncoding="
                + dashIfEmpty(r.getCharacterEncoding())
                + ", headers="
                + joinRequestHeaders(r)
                + "}";
    }

    /** Printable summary matching HttpServletResponse state after the controller runs. */
    private static String describeResponse(HttpServletResponse r) {
        return "HttpServletResponse{"
                + "status="
                + r.getStatus()
                + ", contentType="
                + dashIfEmpty(r.getContentType())
                + ", characterEncoding="
                + dashIfEmpty(r.getCharacterEncoding())
                + ", bufferSize="
                + r.getBufferSize()
                + ", committed="
                + r.isCommitted()
                + ", locale="
                + r.getLocale()
                + ", headers="
                + joinResponseHeaders(r)
                + "}";
    }

    private static String joinRequestHeaders(HttpServletRequest r) {
        StringBuilder sb = new StringBuilder();
        Enumeration<String> names = r.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (isSensitiveHeader(name)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(truncateHeaderValue(r.getHeader(name)));
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    private static String joinResponseHeaders(HttpServletResponse r) {
        StringBuilder sb = new StringBuilder();
        for (String name : r.getHeaderNames()) {
            if (isSensitiveHeader(name)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(truncateHeaderValue(r.getHeader(name)));
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    private static boolean isSensitiveHeader(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("authorization")
                || n.contains("cookie")
                || n.contains("set-cookie")
                || n.contains("proxy-authorization");
    }

    private static String truncateHeaderValue(String v) {
        if (v == null) {
            return "-";
        }
        if (v.length() <= MAX_HEADER_VALUE_CHARS) {
            return v;
        }
        return v.substring(0, MAX_HEADER_VALUE_CHARS) + "…";
    }

    private static String dashIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? "-" : s;
    }

    /**
     * For {@code /api/admin/logs}, omit body text so the in-memory log appender is not flooded with log payloads.
     */
    private static String preview(byte[] raw, String requestUri, boolean isRequestSide) {
        if (requestUri != null && requestUri.startsWith("/api/admin/logs")) {
            int n = raw == null ? 0 : raw.length;
            if (n == 0) {
                return "-";
            }
            String side = isRequestSide ? "request" : "response";
            return "[omitted " + side + " body, " + n + " bytes — use server console file log for full capture]";
        }
        if (requestUri != null && requestUri.startsWith("/api/auth/")) {
            int n = raw == null ? 0 : raw.length;
            if (n == 0) {
                return "-";
            }
            String side = isRequestSide ? "request" : "response";
            return "[redacted " + side + " body for auth route, " + n + " bytes]";
        }
        if (raw == null || raw.length == 0) {
            return "-";
        }
        String s = new String(raw, StandardCharsets.UTF_8);
        if (!isMostlyPrintable(s)) {
            return "[" + raw.length + " bytes binary]";
        }
        s = s.replace('\r', ' ').replace('\n', ' ').trim();
        if (s.length() > MAX_BODY_CHARS) {
            return s.substring(0, MAX_BODY_CHARS) + "…";
        }
        return s.isEmpty() ? "-" : s;
    }

    private static boolean isMostlyPrintable(String s) {
        int bad = 0;
        int n = Math.min(s.length(), 512);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                bad++;
            }
        }
        return bad * 10 < n;
    }
}
