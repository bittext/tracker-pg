package com.svp.tracker.member.controller;

import com.svp.tracker.auth.dto.AuthTokenDto;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.auth.security.TrackerUserPrincipal;
import com.svp.tracker.member.dto.ContactFeedbackRequestDto;
import com.svp.tracker.member.dto.MeCredentialsUpdateRequestDto;
import com.svp.tracker.member.dto.MeMemberProfileRequestDto;
import com.svp.tracker.member.dto.MeMemberProfileResponseDto;
import com.svp.tracker.member.dto.MePasswordChangeRequestDto;
import com.svp.tracker.member.dto.MeOnboardingStatusDto;
import com.svp.tracker.member.dto.UsPostalValidationResponseDto;
import com.svp.tracker.member.service.MemberContactFeedbackService;
import com.svp.tracker.member.service.MemberOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeMemberController {

    private final MemberOnboardingService memberOnboardingService;
    private final MemberContactFeedbackService memberContactFeedbackService;
    private final CurrentUserService currentUser;

    @GetMapping("/onboarding-status")
    public MeOnboardingStatusDto onboardingStatus() {
        return memberOnboardingService.status(requirePrincipal());
    }

    @PostMapping("/onboarding/credentials")
    public AuthTokenDto updateCredentials(@Valid @RequestBody MeCredentialsUpdateRequestDto body) {
        return memberOnboardingService.updateCredentials(requirePrincipal().id(), body);
    }

    @PostMapping("/onboarding/complete")
    public void completeOnboarding() {
        memberOnboardingService.finishOnboarding(requirePrincipal().id());
    }

    @GetMapping("/member-profile")
    public MeMemberProfileResponseDto getMemberProfile() {
        return memberOnboardingService.getProfile(requirePrincipal().id());
    }

    @PutMapping("/member-profile")
    public MeMemberProfileResponseDto saveMemberProfile(@Valid @RequestBody MeMemberProfileRequestDto body) {
        return memberOnboardingService.saveProfile(requirePrincipal().id(), body);
    }

    /**
     * Records in-app acknowledgment of Privacy policy (financial data & Plaid). Required before Plaid Link token /
     * exchange when enforcement is enabled.
     */
    @PostMapping("/privacy/plaid-financial-data-notice")
    public MeMemberProfileResponseDto acceptPlaidFinancialDataNotice() {
        return memberOnboardingService.acceptPlaidFinancialDataNotice(requirePrincipal().id());
    }

    @PostMapping("/password")
    public AuthTokenDto changePassword(@Valid @RequestBody MePasswordChangeRequestDto body) {
        return memberOnboardingService.changePassword(requirePrincipal().id(), body);
    }

    @PostMapping("/contact-feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void contactFeedback(@Valid @RequestBody ContactFeedbackRequestDto body) {
        var p = requirePrincipal();
        memberContactFeedbackService.submit(p.id(), p.username(), body);
    }

    @GetMapping("/address/validate-us-postal")
    public UsPostalValidationResponseDto validateUsPostal(@RequestParam("postalCode") String postalCode) {
        return memberOnboardingService.validateUsPostal(postalCode);
    }

    private TrackerUserPrincipal requirePrincipal() {
        return currentUser
                .currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
