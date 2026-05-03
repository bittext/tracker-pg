package com.svp.tracker.member.service;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.domain.AppUserRole;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.config.FeedbackProperties;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.member.domain.MemberProfile;
import com.svp.tracker.member.dto.ContactFeedbackRequestDto;
import com.svp.tracker.member.repository.MemberProfileRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MemberContactFeedbackService {

    private final AppUserRepository appUserRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final FeedbackProperties feedbackProperties;
    private final FinanceAlertProperties financeAlertProperties;
    private final MemberTransactionalEmailService memberTransactionalEmailService;

    @Transactional(readOnly = true)
    public void submit(long userId, String username, ContactFeedbackRequestDto req) {
        if (!financeAlertProperties.emailProviderConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The server is not configured to send email (SES). Set tracker.finance.alerts email-from, email-enabled, and aws-region.");
        }
        List<String> recipients = resolveAdminEmails();
        if (recipients.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No admin notification addresses were found. Ensure ADMIN users have a saved member profile email, or set tracker.feedback.fallback-admin-emails.");
        }
        AppUser sender = appUserRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        String replyToEmail = memberProfileRepository
                .findByUserId(userId)
                .map(MemberProfile::getEmail)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(e -> e.contains("@"))
                .orElse(null);
        if (!StringUtils.hasText(replyToEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Save a personal email on your member profile (Admin → My profile) before sending feedback so administrators can reply to you.");
        }
        String label = StringUtils.hasText(req.displayName()) ? req.displayName().trim() : "Anonymous";
        if (label.length() > 80) {
            label = label.substring(0, 80);
        }
        Long memberPublicId = sender.getMemberPublicId();
        String body =
                """
                Tracker — member feedback
                ---------------------------

                From (member-chosen name): %s

                Subject:
                %s

                Message:
                %s

                ---
                Support reference (for staff only; not shown publicly):
                Sign-in username: %s
                Internal user id: %d
                Member public id: %s

                Reply-To (member profile email): %s
                """
                        .formatted(
                                label,
                                req.subject().trim(),
                                req.details().trim(),
                                username,
                                userId,
                                memberPublicId != null ? memberPublicId.toString() : "(not assigned yet)",
                                replyToEmail);
        String subject = "[Tracker feedback] " + trimSubject(req.subject());
        if (!memberTransactionalEmailService.sendFeedbackToAdmins(recipients, subject, body, replyToEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Email delivery failed. Please try again later or contact support another way.");
        }
    }

    private List<String> resolveAdminEmails() {
        Set<String> emails = new LinkedHashSet<>();
        for (AppUser admin : appUserRepository.findByRole(AppUserRole.ADMIN)) {
            memberProfileRepository
                    .findByUserId(admin.getId())
                    .map(p -> p.getEmail())
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .ifPresent(emails::add);
        }
        emails.addAll(feedbackProperties.fallbackEmailList());
        return new ArrayList<>(emails);
    }

    private static String trimSubject(String s) {
        String t = s.trim();
        if (t.length() <= 180) {
            return t;
        }
        return t.substring(0, 177) + "...";
    }
}
