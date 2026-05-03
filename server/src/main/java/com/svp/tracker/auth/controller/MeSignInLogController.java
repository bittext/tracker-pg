package com.svp.tracker.auth.controller;

import com.svp.tracker.auth.dto.AuthLoginEventResponseDto;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.auth.security.TrackerUserPrincipal;
import com.svp.tracker.auth.service.LoginAuditService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sign-in audit for the current user only (events tied to their user id or pre-account failed attempts for their
 * username). Admins continue to use {@code /api/admin/auth/login-events} for the global log.
 */
@RestController
@RequestMapping("/api/me/sign-in-log")
@RequiredArgsConstructor
public class MeSignInLogController {

    private final LoginAuditService loginAuditService;
    private final CurrentUserService currentUser;

    @GetMapping
    public List<AuthLoginEventResponseDto> list(
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
            @RequestParam(name = "q", required = false) String q) {
        TrackerUserPrincipal p = currentUser
                .currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        return loginAuditService.listMine(p.id(), p.username(), limit, q);
    }
}
