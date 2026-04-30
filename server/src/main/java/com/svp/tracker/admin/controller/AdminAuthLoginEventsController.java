package com.svp.tracker.admin.controller;

import com.svp.tracker.auth.dto.AuthLoginEventResponseDto;
import com.svp.tracker.auth.service.LoginAuditService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth/login-events")
@RequiredArgsConstructor
public class AdminAuthLoginEventsController {

    private final LoginAuditService loginAuditService;

    @GetMapping
    public List<AuthLoginEventResponseDto> list(
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
            @RequestParam(name = "q", required = false) String q) {
        return loginAuditService.listRecent(limit, q);
    }
}
