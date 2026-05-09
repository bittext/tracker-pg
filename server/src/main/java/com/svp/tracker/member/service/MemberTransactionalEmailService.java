package com.svp.tracker.member.service;

import com.svp.tracker.config.ApplicationBranding;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.WebProperties;
import com.svp.tracker.mail.OutboundEmailSender;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Security/account emails (first profile, password changed). Not gated by marketing opt-in. Uses {@link
 * OutboundEmailSender} (Amazon SES or SMTP) when {@link FinanceAlertProperties#emailProviderConfigured()} is true.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberTransactionalEmailService {

    private final FinanceAlertProperties financeAlertProperties;
    private final WebProperties webProperties;
    private final OutboundEmailSender outboundEmailSender;

    public void sendFirstProfileCreated(String toEmail, String username, long memberPublicId, long internalUserId) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        if (!financeAlertProperties.emailProviderConfigured()) {
            log.debug("Skipping first-profile email: outbound email not configured (tracker.finance.alerts)");
            return;
        }
        String signIn = signInUrl();
        String body =
                """
                Hello,

                Your Health Tracker & PFM member profile has been saved for the first time. Please keep these details for your records:

                Sign-in name (username): %s
                Member ID (public): %d
                Internal user ID (for support): %d

                Open the application and sign in:
                %s

                If you did not create this profile, contact your administrator immediately.

                — %s
                """
                        .formatted(username, memberPublicId, internalUserId, signIn, ApplicationBranding.DISPLAY_NAME);
        send(toEmail.trim(), "Your Health Tracker & PFM member ID and sign-in link", body);
    }

    /**
     * Sent after an administrator creates a login. Does not include the password; the admin should share credentials
     * out-of-band.
     */
    public void sendAdminProvisionedWelcome(String toEmail, String username, String roleName) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        if (!financeAlertProperties.emailProviderConfigured()) {
            log.debug("Skipping admin welcome email: outbound email not configured");
            return;
        }
        String signIn = signInUrl();
        String body =
                """
                Hello,

                An administrator created a Health Tracker & PFM account for you.

                Sign-in name (username): %s
                Role: %s

                Open the application and sign in with the password your administrator gave you (it is not included in this email). We recommend changing your password after first sign-in.

                %s

                If you were not expecting this account, contact your administrator.

                — %s
                """
                        .formatted(username, roleName, signIn, ApplicationBranding.DISPLAY_NAME);
        send(toEmail.trim(), "Your Health Tracker & PFM account is ready", body);
    }

    public void sendPasswordChangedNotice(String toEmail, String username) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        if (!financeAlertProperties.emailProviderConfigured()) {
            log.debug("Skipping password-changed email: outbound email not configured");
            return;
        }
        String signIn = signInUrl();
        String body =
                """
                Hello,

                The password for Health Tracker & PFM account "%s" was just changed.

                Sign in again here:
                %s

                If you did not change your password, sign in if you can and change it again, then contact your administrator.

                — %s
                """
                        .formatted(username, signIn, ApplicationBranding.DISPLAY_NAME);
        send(toEmail.trim(), "Your Health Tracker & PFM password was changed", body);
    }

    /**
     * Sends one message to every admin inbox (member feedback). Not gated by marketing opt-in. Sets Reply-To to {@code
     * replyToEmail} so administrator replies go to the member's personal inbox.
     */
    public boolean sendFeedbackToAdmins(
            List<String> toAddresses, String subjectLine, String bodyText, String replyToEmail) {
        if (toAddresses == null || toAddresses.isEmpty()) {
            return false;
        }
        if (!financeAlertProperties.emailProviderConfigured()) {
            log.debug("Skipping feedback email: outbound email not configured");
            return false;
        }
        List<String> to = new ArrayList<>();
        for (String a : toAddresses) {
            if (StringUtils.hasText(a) && a.contains("@")) {
                to.add(a.trim());
            }
        }
        if (to.isEmpty()) {
            return false;
        }
        List<String> replyTo = null;
        if (StringUtils.hasText(replyToEmail) && replyToEmail.contains("@")) {
            replyTo = List.of(replyToEmail.trim());
        }
        return sendToMany(to, subjectLine, bodyText, replyTo);
    }

    private String signInUrl() {
        return webProperties.publicAppUrl() + "/login";
    }

    private void send(String to, String subject, String bodyText) {
        sendToMany(List.of(to), subject, bodyText, null);
    }

    private boolean sendToMany(
            List<String> toAddresses, String subject, String bodyText, List<String> replyToAddresses) {
        OutboundEmailSender.SendOutcome outcome = outboundEmailSender.sendPlainText(
                financeAlertProperties.emailFrom(), toAddresses, subject, bodyText, replyToAddresses);
        if (!outcome.success()) {
            log.warn("Transactional email failed: {}", outcome.errorDetail());
            return false;
        }
        log.info("Sent transactional account email subject={}", subject);
        return true;
    }
}
