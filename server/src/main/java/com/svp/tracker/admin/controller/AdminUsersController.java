package com.svp.tracker.admin.controller;

import com.svp.tracker.admin.dto.AdminCreateUserRequestDto;
import com.svp.tracker.admin.dto.AdminCreatedUserResponseDto;
import com.svp.tracker.admin.dto.AdminUpdateUserRequestDto;
import com.svp.tracker.admin.dto.AdminUserListItemDto;
import com.svp.tracker.admin.service.AdminUserProvisioningService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUsersController {

    private final AdminUserProvisioningService adminUserProvisioningService;

    @GetMapping
    public List<AdminUserListItemDto> list() {
        return adminUserProvisioningService.listUsers();
    }

    @PostMapping
    public AdminCreatedUserResponseDto create(@Valid @RequestBody AdminCreateUserRequestDto body) {
        return adminUserProvisioningService.createUser(body);
    }

    @PatchMapping("/{id}")
    public AdminUserListItemDto update(@PathVariable long id, @RequestBody AdminUpdateUserRequestDto body) {
        return adminUserProvisioningService.updateUser(id, body);
    }
}
