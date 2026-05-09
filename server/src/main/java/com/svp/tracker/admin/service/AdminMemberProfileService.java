package com.svp.tracker.admin.service;

import com.svp.tracker.admin.dto.AdminMemberProfileDetailDto;
import com.svp.tracker.admin.dto.AdminMemberProfileListItemDto;
import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.member.domain.MemberProfile;
import com.svp.tracker.member.repository.MemberProfileRepository;
import com.svp.tracker.member.service.MemberOnboardingService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminMemberProfileService {

    private final AppUserRepository appUserRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final MemberOnboardingService memberOnboardingService;

    /**
     * Accounts that have saved the member profile form at least once ({@code member_public_id} is set). Ordered by
     * username.
     */
    @Transactional(readOnly = true)
    public List<AdminMemberProfileListItemDto> listUsersWithSavedMemberProfile() {
        List<AppUser> users = appUserRepository.findAllByMemberPublicIdIsNotNullOrderByUsernameAsc();
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.stream().map(AppUser::getId).toList();
        List<MemberProfile> profiles = memberProfileRepository.findAllById(ids);
        Map<Long, MemberProfile> byUserId = new LinkedHashMap<>();
        for (MemberProfile p : profiles) {
            byUserId.put(p.getUserId(), p);
        }
        List<AdminMemberProfileListItemDto> out = new ArrayList<>(users.size());
        for (AppUser u : users) {
            MemberProfile p = byUserId.get(u.getId());
            out.add(new AdminMemberProfileListItemDto(
                    u.getId(),
                    u.getUsername(),
                    u.getMemberPublicId(),
                    displayName(p),
                    u.getOnboardingCompletedAt() != null));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public AdminMemberProfileDetailDto getMemberProfileForAdmin(long targetUserId) {
        AppUser target =
                appUserRepository.findById(targetUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getMemberPublicId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "That account has not saved a member profile yet.");
        }
        var profile = memberOnboardingService.getProfile(targetUserId);
        return new AdminMemberProfileDetailDto(
                target.getId(),
                target.getUsername(),
                target.getRole().name(),
                target.getOnboardingCompletedAt() != null,
                profile);
    }

    private static String displayName(MemberProfile p) {
        if (p == null) {
            return "";
        }
        String fn = p.getFirstName() != null ? p.getFirstName().trim() : "";
        String ln = p.getLastName() != null ? p.getLastName().trim() : "";
        String s = (fn + " " + ln).trim();
        return s;
    }
}
