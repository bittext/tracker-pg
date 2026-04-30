package com.svp.tracker.auth.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.svp.tracker.auth.config.AuthProperties;
import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.domain.AppUserRole;
import com.svp.tracker.auth.domain.AuthLoginEventType;
import com.svp.tracker.auth.domain.MfaChallenge;
import com.svp.tracker.auth.domain.TrustedLocation;
import com.svp.tracker.auth.dto.AuthTokenDto;
import com.svp.tracker.auth.dto.LoginRequestDto;
import com.svp.tracker.auth.dto.LoginResponseDto;
import com.svp.tracker.auth.dto.MfaVerifyRequestDto;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.repository.MfaChallengeRepository;
import com.svp.tracker.auth.repository.TrustedLocationRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final TrustedLocationRepository trustedLocationRepository;
    private final MfaChallengeRepository mfaChallengeRepository;
    private final PasswordHashService passwordHashService;
    private final JwtTokenService jwtTokenService;
    private final LocationFingerprintService locationFingerprintService;
    private final SmsSender smsSender;
    private final AuthProperties authProperties;
    private final LoginAuditService loginAuditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public LoginResponseDto login(LoginRequestDto req, String remoteIp, String userAgent) {
        String un = cleanUsername(req);
        String ip = nvl(remoteIp);
        String ua = nvl(userAgent);
        Optional<AppUser> opt = appUserRepository.findByUsernameIgnoreCase(un);
        if (opt.isEmpty()) {
            loginAuditService.record(
                    AuthLoginEventType.LOGIN_FAILED, null, un.isEmpty() ? "(empty)" : un, ip, ua, "user_not_found");
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }
        AppUser user = opt.get();
        if (!user.isActive()) {
            loginAuditService.record(AuthLoginEventType.LOGIN_FAILED, user.getId(), user.getUsername(), ip, ua, "account_inactive");
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }
        if (!passwordHashService.verify(req.password(), user.getPasswordHash(), user.getPasswordSalt())) {
            loginAuditService.record(AuthLoginEventType.LOGIN_FAILED, user.getId(), user.getUsername(), ip, ua, "invalid_password");
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }

        if (user.getRole() == AppUserRole.ADMIN || !user.isMfaEnabled()) {
            var token = issueToken(user);
            loginAuditService.record(
                    AuthLoginEventType.LOGIN_SUCCESS, user.getId(), user.getUsername(), ip, ua, "password");
            return new LoginResponseDto(false, null, "Login successful", token);
        }

        String locationHash = locationFingerprintService.fingerprint(
                remoteIp, userAgent, req.locationFingerprintSource());
        Instant now = Instant.now();
        Instant trustedSince = now.minusSeconds(authProperties.trustedLocationDays() * 24L * 60L * 60L);
        TrustedLocation knownLocation = trustedLocationRepository
                .findByUserAndLocationHash(user, locationHash)
                .filter(l -> l.getLastSeenAt() != null && l.getLastSeenAt().isAfter(trustedSince))
                .orElse(null);
        if (knownLocation != null) {
            knownLocation.setLastSeenAt(now);
            trustedLocationRepository.save(knownLocation);
            var token = issueToken(user);
            loginAuditService.record(
                    AuthLoginEventType.LOGIN_SUCCESS, user.getId(), user.getUsername(), ip, ua, "trusted_location");
            return new LoginResponseDto(false, null, "Login successful", token);
        }

        if (user.getPhoneE164() == null || user.getPhoneE164().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "MFA phone is not configured for this user");
        }
        Instant lastHour = now.minusSeconds(3600);
        long recent = mfaChallengeRepository.countByUser_IdAndCreatedAtAfter(user.getId(), lastHour);
        if (recent >= authProperties.mfaRateLimitPerHour()) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many MFA requests, try again later");
        }

        String otp = generateOtp();
        var otpHash = passwordHashService.create(otp);
        MfaChallenge challenge = new MfaChallenge();
        challenge.setUser(user);
        challenge.setOtpHash(otpHash.hash());
        challenge.setOtpSalt(otpHash.salt());
        challenge.setAttempts(0);
        challenge.setMaxAttempts(authProperties.mfaMaxAttempts());
        challenge.setLocationHash(locationHash);
        challenge.setExpiresAt(now.plusSeconds(authProperties.mfaOtpTtlSeconds()));
        mfaChallengeRepository.save(challenge);
        smsSender.sendOtp(user.getPhoneE164(), otp);
        loginAuditService.record(
                AuthLoginEventType.MFA_REQUIRED, user.getId(), user.getUsername(), ip, ua, "challenge=" + challenge.getId());

        return new LoginResponseDto(
                true,
                challenge.getId(),
                "MFA required. Enter the SMS code to complete sign-in.",
                null);
    }

    @Transactional
    public AuthTokenDto verifyMfa(MfaVerifyRequestDto req, String remoteIp, String userAgent) {
        String ip = nvl(remoteIp);
        String ua = nvl(userAgent);
        MfaChallenge challenge = mfaChallengeRepository
                .findByIdAndUsedAtIsNull(req.challengeId())
                .orElse(null);
        if (challenge == null) {
            loginAuditService.record(
                    AuthLoginEventType.MFA_FAILED, null, "(unknown)", ip, ua, "invalid_challenge");
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid challenge");
        }
        AppUser preUser = challenge.getUser();
        String preName = preUser != null ? preUser.getUsername() : "(unknown)";
        Long preUid = preUser != null ? preUser.getId() : null;
        Instant now = Instant.now();
        if (challenge.getExpiresAt().isBefore(now)) {
            loginAuditService.record(
                    AuthLoginEventType.MFA_FAILED, preUid, preName, ip, ua, "challenge_expired");
            throw new ResponseStatusException(UNAUTHORIZED, "MFA code expired");
        }
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            loginAuditService.record(
                    AuthLoginEventType.MFA_FAILED, preUid, preName, ip, ua, "max_attempts");
            throw new ResponseStatusException(UNAUTHORIZED, "MFA challenge locked");
        }

        boolean otpValid = passwordHashService.verify(req.otpCode(), challenge.getOtpHash(), challenge.getOtpSalt());
        if (!otpValid) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            mfaChallengeRepository.save(challenge);
            loginAuditService.record(
                    AuthLoginEventType.MFA_FAILED, preUid, preName, ip, ua, "invalid_otp");
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid MFA code");
        }

        challenge.setUsedAt(now);
        mfaChallengeRepository.save(challenge);
        AppUser user = challenge.getUser();
        String locationHash = locationFingerprintService.fingerprint(
                remoteIp, userAgent, req.locationFingerprintSource());
        TrustedLocation trusted = trustedLocationRepository
                .findByUserAndLocationHash(user, locationHash)
                .orElseGet(TrustedLocation::new);
        trusted.setUser(user);
        trusted.setLocationHash(locationHash);
        trusted.setDisplayLabel(req.locationLabel());
        if (trusted.getFirstSeenAt() == null) {
            trusted.setFirstSeenAt(now);
        }
        trusted.setLastSeenAt(now);
        trustedLocationRepository.save(trusted);
        var token = issueToken(user);
        loginAuditService.record(
                AuthLoginEventType.LOGIN_SUCCESS, user.getId(), user.getUsername(), ip, ua, "mfa");
        return token;
    }

    private AuthTokenDto issueToken(AppUser user) {
        var issued = jwtTokenService.issue(user);
        return new AuthTokenDto(issued.token(), issued.expiresAt(), user.getUsername(), user.getRole().name());
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private static String cleanUsername(LoginRequestDto req) {
        if (req.username() == null) {
            return "";
        }
        return req.username().trim();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
