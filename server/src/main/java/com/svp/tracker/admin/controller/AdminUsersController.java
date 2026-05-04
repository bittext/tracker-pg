package com.svp.tracker.admin.controller;

import com.svp.tracker.admin.dto.AdminCreateUserRequestDto;
import com.svp.tracker.admin.dto.AdminCreatedUserResponseDto;
import com.svp.tracker.admin.service.AdminUserProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUsersController {

    private final AdminUserProvisioningService adminUserProvisioningService;

    @PostMapping
    public AdminCreatedUserResponseDto create(@Valid @RequestBody AdminCreateUserRequestDto body) {
        return adminUserProvisioningService.createUser(body);
    }
}
