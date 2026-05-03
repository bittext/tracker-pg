package com.svp.tracker.member.service;

import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.WebProperties;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Security/account emails (first profile, password changed). Not gated by marketing opt-in. Uses the same SES
 * configuration as finance alerts when {@link FinanceAlertProperties#emailProviderConfigured()} is true.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberTransactionalEmailService {

    private final FinanceAlertProperties financeAlertProperties;
    private final WebProperties webProperties;

    private final Object clientLock = new Object();
    private volatile SesV2Client sesClient;

    public void sendFirstProfileCreated(String toEmail, String username, long memberPublicId, long internalUserId) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        if (!financeAlertProperties.emailProviderConfigured()) {
            log.debug("Skipping first-profile email: SES not configured (tracker.finance.alerts email-from / region)");
            return;
        }
        String signIn = signInUrl();
        String body =
                """
                Hello,

                Your Tracker member profile has been saved for the first time. Please keep these details for your records:

                Sign-in name (username): %s
                Member ID (public): %d
                Internal user ID (for support): %d

                Open the application and sign in:
                %s

                If you did not create this profile, contact your administrator immediately.

                — Tracker
                """
                        .formatted(username, memberPublicId, internalUserId, signIn);
        send(toEmail.trim(), "Your Tracker member ID and sign-in link", body);
    }

    public void sendPasswordChangedNotice(String toEmail, String username) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        if (!financeAlertProperties.emailProviderConfigured()) {
            log.debug("Skipping password-changed email: SES not configured");
            return;
        }
        String signIn = signInUrl();
        String body =
                """
                Hello,

                The password for Tracker account "%s" was just changed.

                Sign in again here:
                %s

                If you did not change your password, sign in if you can and change it again, then contact your administrator.

                — Tracker
                """
                        .formatted(username, signIn);
        send(toEmail.trim(), "Your Tracker password was changed", body);
    }

    /**
     * Sends one message to every admin inbox (member feedback). Not gated by marketing opt-in. Sets SES
     * {@code Reply-To} to {@code replyToEmail} so administrator replies go to the member's personal inbox.
     */
    public boolean sendFeedbackToAdmins(
            List<String> toAddresses, String subjectLine, String bodyText, String replyToEmail) {
        if (toAddresses == null || toAddresses.isEmpty()) {
            return false;
        }
        if (!financeAlertProperties.emailProviderConfigured()) {
            log.debug("Skipping feedback email: SES not configured");
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

    /**
     * @param replyToAddresses when non-null and non-empty, set as SES Reply-To (e.g. member's profile email for
     *     feedback).
     * @return true if the message was accepted by SES
     */
    private boolean sendToMany(
            List<String> toAddresses, String subject, String bodyText, List<String> replyToAddresses) {
        SesV2Client client = sesClient();
        if (client == null) {
            log.warn("Transactional email skipped: SES client unavailable");
            return false;
        }
        try {
            var reqBuilder = SendEmailRequest.builder()
                    .fromEmailAddress(financeAlertProperties.emailFrom())
                    .destination(Destination.builder().toAddresses(toAddresses).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder()
                                            .data(subject)
                                            .charset("UTF-8")
                                            .build())
                                    .body(Body.builder()
                                            .text(Content.builder()
                                                    .data(bodyText)
                                                    .charset("UTF-8")
                                                    .build())
                                            .build())
                                    .build())
                            .build());
            if (replyToAddresses != null && !replyToAddresses.isEmpty()) {
                reqBuilder.replyToAddresses(replyToAddresses);
            }
            SendEmailRequest req = reqBuilder.build();
            client.sendEmail(req);
            log.info("Sent transactional account email subject={}", subject);
            return true;
        } catch (AwsServiceException e) {
            log.warn(
                    "Transactional SES email failed: {}",
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Transactional SES email failed", e);
            return false;
        }
    }

    private SesV2Client sesClient() {
        if (!financeAlertProperties.emailProviderConfigured()) {
            return null;
        }
        synchronized (clientLock) {
            if (sesClient == null) {
                sesClient = SesV2Client.builder()
                        .region(Region.of(financeAlertProperties.awsRegion()))
                        .build();
            }
            return sesClient;
        }
    }

    @PreDestroy
    void closeSes() {
        synchronized (clientLock) {
            if (sesClient != null) {
                sesClient.close();
                sesClient = null;
            }
        }
    }
}
