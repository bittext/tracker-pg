package com.svp.tracker.auth.controller;

import com.svp.tracker.auth.domain.AuthLoginEventType;
import com.svp.tracker.auth.dto.AuthTokenDto;
import com.svp.tracker.auth.dto.LoginRequestDto;
import com.svp.tracker.auth.dto.LoginResponseDto;
import com.svp.tracker.auth.dto.LogoutRequestDto;
import com.svp.tracker.auth.dto.MeSessionDto;
import com.svp.tracker.auth.dto.MfaVerifyRequestDto;
import com.svp.tracker.auth.dto.StepUpRequestDto;
import com.svp.tracker.auth.dto.StepUpTokenDto;
import com.svp.tracker.auth.security.TrackerUserPrincipal;
import com.svp.tracker.auth.service.AuthService;
import com.svp.tracker.auth.service.LoginAuditService;
import com.svp.tracker.auth.service.StepUpAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginAuditService loginAuditService;
    private final StepUpAuthService stepUpAuthService;

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto body, HttpServletRequest request) {
        return authService.login(body, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/mfa/verify")
    public AuthTokenDto verifyMfa(@Valid @RequestBody MfaVerifyRequestDto body, HttpServletRequest request) {
        return authService.verifyMfa(body, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            Authentication auth,
            HttpServletRequest request,
            @RequestBody(required = false) LogoutRequestDto body) {
        if (auth != null && auth.getPrincipal() instanceof TrackerUserPrincipal p) {
            String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "";
            String ua = request.getHeader("User-Agent");
            if (ua == null) {
                ua = "";
            }
            String locationLabel = body != null ? body.locationLabel() : null;
            loginAuditService.record(
                    AuthLoginEventType.LOGOUT, p.id(), p.username(), ip, ua, null, locationLabel);
        }
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Logged out"));
    }

    @GetMapping("/me")
    public MeSessionDto me(Authentication auth) {
        TrackerUserPrincipal p = requirePrincipal(auth);
        return new MeSessionDto(p.id(), p.username(), p.role().name(), p.canAccessMarkets(), p.isAdmin());
    }

    @PostMapping("/step-up")
    public StepUpTokenDto stepUp(Authentication auth, @Valid @RequestBody StepUpRequestDto body) {
        TrackerUserPrincipal p = requirePrincipal(auth);
        if (!p.canAccessMarkets()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Markets access is not enabled.");
        }
        return stepUpAuthService.issue(p.id(), body.password());
    }

    private static TrackerUserPrincipal requirePrincipal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TrackerUserPrincipal p)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return p;
    }
}
