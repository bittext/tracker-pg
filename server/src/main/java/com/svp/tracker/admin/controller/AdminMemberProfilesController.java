package com.svp.tracker.admin.controller;

import com.svp.tracker.admin.dto.AdminMemberProfileDetailDto;
import com.svp.tracker.admin.dto.AdminMemberProfileListItemDto;
import com.svp.tracker.admin.service.AdminMemberProfileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/member-profiles")
@RequiredArgsConstructor
public class AdminMemberProfilesController {

    private final AdminMemberProfileService adminMemberProfileService;

    /** Members who have saved profile data (member public id assigned). Admin only. */
    @GetMapping
    public List<AdminMemberProfileListItemDto> list() {
        return adminMemberProfileService.listUsersWithSavedMemberProfile();
    }

    /** Read-only member profile for a user who has saved a profile. Admin only. */
    @GetMapping("/{userId}")
    public AdminMemberProfileDetailDto get(@PathVariable("userId") long userId) {
        return adminMemberProfileService.getMemberProfileForAdmin(userId);
    }
}
