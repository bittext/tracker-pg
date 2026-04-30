package com.svp.tracker.auth.service;

import com.svp.tracker.auth.domain.AuthLoginEvent;
import com.svp.tracker.auth.domain.AuthLoginEventType;
import com.svp.tracker.auth.dto.AuthLoginEventResponseDto;
import com.svp.tracker.auth.repository.AuthLoginEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LoginAuditService {

    private static final int MAX_UA = 8_000;

    private final AuthLoginEventRepository authLoginEventRepository;

    public LoginAuditService(AuthLoginEventRepository authLoginEventRepository) {
        this.authLoginEventRepository = authLoginEventRepository;
    }

    /**
     * Commits in a new transaction so rows persist even when the calling login flow rolls back after {@code
     * ResponseStatusException}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuthLoginEventType eventType,
            Long userId,
            String usernameShown,
            String clientIp,
            String userAgent,
            String detail) {
        AuthLoginEvent e = new AuthLoginEvent();
        e.setEventType(eventType);
        e.setUserId(userId);
        e.setUsernameShown(s(usernameShown, 120));
        e.setClientIp(s(clientIp, 64));
        e.setUserAgent(trimUa(userAgent));
        e.setDetail(s(detail, 500));
        e.setCreatedAt(Instant.now());
        authLoginEventRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<AuthLoginEventResponseDto> listRecent(int limit, String searchQuery) {
        int n = Math.min(500, Math.max(1, limit));
        PageRequest page = PageRequest.of(0, n, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuthLoginEvent> pageResult =
                StringUtils.hasText(searchQuery) ? authLoginEventRepository.search(searchQuery.trim(), page) : authLoginEventRepository.findAllByOrderByCreatedAtDesc(page);
        return pageResult.getContent().stream().map(LoginAuditService::toDto).toList();
    }

    private static AuthLoginEventResponseDto toDto(AuthLoginEvent e) {
        return new AuthLoginEventResponseDto(
                e.getId(),
                e.getEventType() != null ? e.getEventType().name() : "",
                e.getUserId(),
                e.getUsernameShown() != null ? e.getUsernameShown() : "",
                e.getClientIp() != null ? e.getClientIp() : "",
                e.getUserAgent() != null ? e.getUserAgent() : "",
                e.getDetail() != null ? e.getDetail() : "",
                e.getCreatedAt());
    }

    private static String s(String v, int max) {
        if (v == null) {
            return "";
        }
        String t = v.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }

    private static String trimUa(String ua) {
        if (ua == null) {
            return null;
        }
        if (ua.length() <= MAX_UA) {
            return ua;
        }
        return ua.substring(0, MAX_UA) + "…";
    }
}
