package com.svp.tracker.admin.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import com.svp.tracker.admin.dto.AdminCreateUserRequestDto;
import com.svp.tracker.admin.dto.AdminCreatedUserResponseDto;
import com.svp.tracker.admin.dto.AdminUpdateUserRequestDto;
import com.svp.tracker.admin.dto.AdminUserListItemDto;
import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.domain.AppUserRole;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.service.PasswordHashService;
import com.svp.tracker.member.repository.MemberProfileRepository;
import com.svp.tracker.member.service.MemberTransactionalEmailService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminUserProvisioningService {

    private final AppUserRepository appUserRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final PasswordHashService passwordHashService;
    private final MemberTransactionalEmailService memberTransactionalEmailService;

    @Transactional
    public AdminCreatedUserResponseDto createUser(AdminCreateUserRequestDto req) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        if (username.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Username is required.");
        }
        String emailNorm = req.email().trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(emailNorm)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is required.");
        }
        AppUserRole role = req.role();
        if (role != AppUserRole.USER && role != AppUserRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "role must be USER or ADMIN.");
        }
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(CONFLICT, "That username is already taken.");
        }
        if (appUserRepository.existsAnyAuthUserWithNormalizedEmail(emailNorm)
                || memberProfileRepository.existsAnyMemberProfileWithNormalizedEmail(emailNorm)) {
            throw new ResponseStatusException(CONFLICT, "That email is already used by another account.");
        }

        var ph = passwordHashService.create(req.password());
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(emailNorm);
        user.setPasswordHash(ph.hash());
        user.setPasswordSalt(ph.salt());
        user.setRole(role);
        user.setMfaEnabled(req.mfaEnabled());
        user.setActive(req.active());
        user.setMarketsEnabled(req.marketsEnabled());
        user.setPhoneE164(null);
        user = appUserRepository.save(user);

        final String mailTo = emailNorm;
        final String uname = user.getUsername();
        final String roleName = user.getRole().name();
        runAfterCommit(() -> memberTransactionalEmailService.sendAdminProvisionedWelcome(mailTo, uname, roleName));

        return new AdminCreatedUserResponseDto(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public java.util.List<AdminUserListItemDto> listUsers() {
        return appUserRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new AdminUserListItemDto(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getRole().name(),
                        u.isActive(),
                        u.isMfaEnabled(),
                        u.isMarketsEnabled()))
                .toList();
    }

    @Transactional
    public AdminUserListItemDto updateUser(long id, AdminUpdateUserRequestDto req) {
        AppUser user = appUserRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found."));
        if (req.active() != null) {
            user.setActive(req.active());
        }
        if (req.marketsEnabled() != null) {
            user.setMarketsEnabled(req.marketsEnabled());
        }
        if (req.mfaEnabled() != null) {
            user.setMfaEnabled(req.mfaEnabled());
        }
        user = appUserRepository.save(user);
        return new AdminUserListItemDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive(),
                user.isMfaEnabled(),
                user.isMarketsEnabled());
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
