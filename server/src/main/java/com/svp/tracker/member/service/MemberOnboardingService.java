package com.svp.tracker.member.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.dto.AuthTokenDto;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.security.TrackerUserPrincipal;
import com.svp.tracker.auth.service.JwtTokenService;
import com.svp.tracker.auth.service.PasswordHashService;
import com.svp.tracker.member.domain.MemberProfile;
import com.svp.tracker.member.dto.MeCredentialsUpdateRequestDto;
import com.svp.tracker.member.dto.MeMemberProfileRequestDto;
import com.svp.tracker.member.dto.MePasswordChangeRequestDto;
import com.svp.tracker.member.dto.MeMemberProfileResponseDto;
import com.svp.tracker.member.dto.MeOnboardingStatusDto;
import com.svp.tracker.member.dto.UsPostalValidationResponseDto;
import com.svp.tracker.member.repository.MemberProfileRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MemberOnboardingService {

    private final AppUserRepository appUserRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final PasswordHashService passwordHashService;
    private final JwtTokenService jwtTokenService;
    private final UsPostalValidationService usPostalValidationService;
    private final MemberTransactionalEmailService memberTransactionalEmailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public MeOnboardingStatusDto status(TrackerUserPrincipal principal) {
        AppUser user = loadUser(principal.id());
        return toStatus(user);
    }

    @Transactional
    public AuthTokenDto updateCredentials(long userId, MeCredentialsUpdateRequestDto req) {
        AppUser user = loadUser(userId);
        if (user.getOnboardingCompletedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Onboarding is already complete for this account.");
        }
        if (!passwordHashService.verify(req.currentPassword(), user.getPasswordHash(), user.getPasswordSalt())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Current password is incorrect.");
        }
        boolean changed = false;
        boolean passwordChanged = false;
        String newUsername = trimToNull(req.newUsername());
        String newPassword = req.newPassword();
        if (StringUtils.hasText(newUsername) && !newUsername.equalsIgnoreCase(user.getUsername())) {
            if (appUserRepository.existsByUsernameIgnoreCaseAndIdNot(newUsername, user.getId())) {
                throw new ResponseStatusException(CONFLICT, "That username is already taken.");
            }
            user.setUsername(newUsername);
            changed = true;
        }
        if (StringUtils.hasText(newPassword)) {
            if (newPassword.length() < 8) {
                throw new ResponseStatusException(BAD_REQUEST, "New password must be at least 8 characters.");
            }
            var ph = passwordHashService.create(newPassword);
            user.setPasswordHash(ph.hash());
            user.setPasswordSalt(ph.salt());
            changed = true;
            passwordChanged = true;
        }
        if (!changed) {
            throw new ResponseStatusException(BAD_REQUEST, "Provide a new username and/or a new password to continue.");
        }
        user.setCredentialsStepCompletedAt(Instant.now());
        appUserRepository.save(user);
        if (passwordChanged) {
            schedulePasswordChangedEmail(userId, user.getUsername());
        }
        return issueToken(user);
    }

    /**
     * For signed-in members who have finished onboarding: verify the current password and set a new one. Returns a
     * fresh JWT so the session stays valid with the same username.
     */
    @Transactional
    public AuthTokenDto changePassword(long userId, MePasswordChangeRequestDto req) {
        AppUser user = loadUser(userId);
        if (user.getOnboardingCompletedAt() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Finish account setup before changing your password here.");
        }
        if (!passwordHashService.verify(req.currentPassword(), user.getPasswordHash(), user.getPasswordSalt())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Current password is incorrect.");
        }
        if (passwordHashService.verify(req.newPassword(), user.getPasswordHash(), user.getPasswordSalt())) {
            throw new ResponseStatusException(BAD_REQUEST, "New password must be different from your current password.");
        }
        var ph = passwordHashService.create(req.newPassword());
        user.setPasswordHash(ph.hash());
        user.setPasswordSalt(ph.salt());
        appUserRepository.save(user);
        schedulePasswordChangedEmail(userId, user.getUsername());
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public MeMemberProfileResponseDto getProfile(long userId) {
        AppUser user = loadUser(userId);
        return memberProfileRepository
                .findByUserId(userId)
                .map(p -> toDto(p, user.getMemberPublicId()))
                .orElseGet(() -> emptyDto(user.getMemberPublicId()));
    }

    @Transactional
    public MeMemberProfileResponseDto saveProfile(long userId, MeMemberProfileRequestDto req) {
        AppUser user = loadUser(userId);
        if (user.getOnboardingCompletedAt() == null && user.getCredentialsStepCompletedAt() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Complete the sign-in credentials step before saving profile.");
        }
        UsPostalValidationResponseDto zipLookup = usPostalValidationService.lookupUsZip(req.postalCode());
        String vZip = zipLookup.postalCode();
        String vCity = null;
        String vState = null;
        if (!zipLookup.places().isEmpty()) {
            var p0 = zipLookup.places().get(0);
            vCity = blankToNull(p0.placeName());
            vState = blankToNull(p0.stateAbbreviation());
        }

        Optional<MemberProfile> existingProfile = memberProfileRepository.findByUserId(userId);
        boolean isFirstProfileSave = existingProfile.isEmpty();
        MemberProfile profile = existingProfile.orElseGet(() -> {
            MemberProfile mp = new MemberProfile();
            mp.setUser(user);
            return mp;
        });

        profile.setFirstName(req.firstName().trim());
        profile.setMiddleName(trimToNull(req.middleName()));
        profile.setLastName(req.lastName().trim());
        profile.setNickname(trimToNull(req.nickname()));
        profile.setDateOfBirth(req.dateOfBirth());
        profile.setGender(req.gender());
        profile.setEmail(req.email().trim());
        profile.setPhoneCountryCode(normalizeCountryCode(req.phoneCountryCode()));
        profile.setPhoneNationalNumber(digitsOnly(req.phoneNationalNumber()));
        profile.setAddressLine1(req.addressLine1().trim());
        profile.setAddressLine2(trimToNull(req.addressLine2()));
        profile.setCity(req.city().trim());
        profile.setStateRegion(req.stateRegion().trim());
        profile.setPostalCode(req.postalCode().trim());
        profile.setValidatedPostalCode(vZip);
        profile.setValidatedCity(vCity);
        profile.setValidatedStateRegion(vState);
        profile.setAddressUseValidatedSuggestion(req.addressUseValidatedSuggestion());
        if (req.addressUseValidatedSuggestion() && vCity != null && vState != null) {
            profile.setCity(vCity);
            profile.setStateRegion(vState);
            profile.setPostalCode(vZip != null ? vZip : profile.getPostalCode());
        }
        profile.setMarketingEmailOptIn(req.marketingEmailOptIn());
        profile.setMarketingSmsOptIn(req.marketingSmsOptIn());
        memberProfileRepository.save(profile);

        String e164 = toE164(profile.getPhoneCountryCode(), profile.getPhoneNationalNumber());
        if (e164 != null) {
            user.setPhoneE164(e164);
        }
        if (user.getMemberPublicId() == null) {
            user.setMemberPublicId(mintUniqueMemberPublicId());
        }
        appUserRepository.save(user);
        if (isFirstProfileSave
                && user.getMemberPublicId() != null
                && StringUtils.hasText(profile.getEmail())) {
            final String mailTo = profile.getEmail().trim();
            final String uname = user.getUsername();
            final long memPub = user.getMemberPublicId();
            final long uid = user.getId();
            runAfterCommit(() -> memberTransactionalEmailService.sendFirstProfileCreated(mailTo, uname, memPub, uid));
        }
        return toDto(profile, user.getMemberPublicId());
    }

    @Transactional
    public void finishOnboarding(long userId) {
        AppUser user = loadUser(userId);
        if (user.getOnboardingCompletedAt() != null) {
            return;
        }
        if (user.getMemberPublicId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Save your member profile before finishing onboarding.");
        }
        user.setOnboardingCompletedAt(Instant.now());
        appUserRepository.save(user);
    }

    public UsPostalValidationResponseDto validateUsPostal(String postalCode) {
        return usPostalValidationService.lookupUsZip(postalCode);
    }

    private MeOnboardingStatusDto toStatus(AppUser user) {
        boolean completed = user.getOnboardingCompletedAt() != null;
        boolean cred = user.getCredentialsStepCompletedAt() != null;
        boolean profileSubmitted = user.getMemberPublicId() != null;
        return new MeOnboardingStatusDto(completed, cred, profileSubmitted, user.getMemberPublicId(), user.getUsername());
    }

    private AppUser loadUser(long id) {
        return appUserRepository.findById(id).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
    }

    private void schedulePasswordChangedEmail(long userId, String username) {
        memberProfileRepository
                .findByUserId(userId)
                .map(MemberProfile::getEmail)
                .filter(StringUtils::hasText)
                .ifPresent(email -> runAfterCommit(
                        () -> memberTransactionalEmailService.sendPasswordChangedNotice(email.trim(), username)));
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

    private AuthTokenDto issueToken(AppUser user) {
        var issued = jwtTokenService.issue(user);
        return new AuthTokenDto(issued.token(), issued.expiresAt(), user.getUsername(), user.getRole().name());
    }

    private long mintUniqueMemberPublicId() {
        for (int i = 0; i < 40; i++) {
            long candidate = 1_000_000_000L + (long) (secureRandom.nextDouble() * 9_000_000_000L);
            if (!appUserRepository.existsByMemberPublicId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique member id");
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String normalizeCountryCode(String code) {
        String c = code.trim();
        if (!c.startsWith("+")) {
            c = "+" + c.replace("+", "").trim();
        }
        return c;
    }

    private static String digitsOnly(String s) {
        return s.replaceAll("\\D", "");
    }

    private static String toE164(String countryCode, String nationalDigits) {
        if (!StringUtils.hasText(nationalDigits)) {
            return null;
        }
        return countryCode + nationalDigits;
    }

    private static MeMemberProfileResponseDto emptyDto(Long memberPublicId) {
        return new MeMemberProfileResponseDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                memberPublicId);
    }

    private static MeMemberProfileResponseDto toDto(MemberProfile p, Long memberPublicId) {
        return new MeMemberProfileResponseDto(
                p.getFirstName(),
                p.getMiddleName(),
                p.getLastName(),
                p.getNickname(),
                p.getDateOfBirth(),
                p.getGender(),
                p.getEmail(),
                p.getPhoneCountryCode(),
                p.getPhoneNationalNumber(),
                p.getAddressLine1(),
                p.getAddressLine2(),
                p.getCity(),
                p.getStateRegion(),
                p.getPostalCode(),
                p.getValidatedPostalCode(),
                p.getValidatedCity(),
                p.getValidatedStateRegion(),
                p.isAddressUseValidatedSuggestion(),
                p.isMarketingEmailOptIn(),
                p.isMarketingSmsOptIn(),
                memberPublicId);
    }
}
