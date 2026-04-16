package com.svp.tracker.auth.controller;

import com.svp.tracker.auth.dto.AuthTokenDto;
import com.svp.tracker.auth.dto.LoginRequestDto;
import com.svp.tracker.auth.dto.LoginResponseDto;
import com.svp.tracker.auth.dto.MfaVerifyRequestDto;
import com.svp.tracker.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto body, HttpServletRequest request) {
        return authService.login(body, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/mfa/verify")
    public AuthTokenDto verifyMfa(@Valid @RequestBody MfaVerifyRequestDto body, HttpServletRequest request) {
        return authService.verifyMfa(body, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Logged out"));
    }
}
