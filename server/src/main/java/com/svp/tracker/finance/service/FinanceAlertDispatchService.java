package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.finance.domain.FinanceAlertDeliveryChannel;
import com.svp.tracker.finance.domain.FinanceAlertDeliveryStatus;
import com.svp.tracker.finance.domain.FinanceAlertEvent;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.FinanceStockAlert;
import com.svp.tracker.finance.repository.FinanceAlertEventRepository;
import com.svp.tracker.member.domain.MemberProfile;
import com.svp.tracker.member.repository.MemberProfileRepository;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceAlertDispatchService {

    private final FinanceAlertProperties props;
    private final FinanceAlertEventRepository eventRepository;
    private final MemberProfileRepository memberProfileRepository;

    private final Object clientLock = new Object();
    private volatile SnsClient snsClient;
    private volatile SesV2Client sesClient;

    public List<FinanceAlertEvent> dispatchTriggeredAlert(
            FinanceStockAlert alert,
            FinanceNotificationSettings settings,
            String subject,
            String body) {
        List<FinanceAlertEvent> events = new ArrayList<>();
        boolean wantEmail = settings != null && settings.isEmailEnabled();
        boolean wantSms = settings != null && settings.isSmsEnabled();
        EffectiveNotifyChannels ch = effectiveNotifyChannels(alert.getOwnerUserId(), wantEmail, wantSms);
        if (settings == null || (!ch.email() && !ch.sms())) {
            String reason = settings == null || (!wantEmail && !wantSms)
                    ? "No notification channels enabled"
                    : "Email/SMS notifications are turned off in member profile (My profile)";
            events.add(save(systemEvent(
                    alert, FinanceAlertDeliveryChannel.SYSTEM, FinanceAlertDeliveryStatus.SKIPPED, reason, "")));
            return events;
        }
        if (ch.email()) {
            events.add(sendEmail(alert, settings.getEmailAddress(), subject, body));
        }
        if (ch.sms()) {
            events.add(sendSms(alert, settings.getMobileE164(), body));
        }
        return events;
    }

    public FinanceAlertEvent testEmail(long ownerUserId, String emailAddress) {
        FinanceStockAlert pseudo = pseudoAlert(ownerUserId);
        if (!memberAllowsEmail(ownerUserId)) {
            return save(systemEvent(
                    pseudo,
                    FinanceAlertDeliveryChannel.EMAIL,
                    FinanceAlertDeliveryStatus.SKIPPED,
                    "Email notifications are off in your member profile (My profile). Turn them on to receive messages.",
                    ""));
        }
        return sendEmail(
                pseudo,
                emailAddress,
                "Health Tracker & PFM finance alert test",
                "This is a Health Tracker & PFM finance alert test email. Your alert email channel is configured.");
    }

    public FinanceAlertEvent testSms(long ownerUserId, String mobileE164) {
        FinanceStockAlert pseudo = pseudoAlert(ownerUserId);
        if (!memberAllowsSms(ownerUserId)) {
            return save(systemEvent(
                    pseudo,
                    FinanceAlertDeliveryChannel.SMS,
                    FinanceAlertDeliveryStatus.SKIPPED,
                    "SMS notifications are off in your member profile (My profile). Turn them on to receive messages.",
                    ""));
        }
        return sendSms(
                pseudo,
                mobileE164,
                "Health Tracker & PFM finance alert test: your SMS channel is configured.");
    }

    private FinanceAlertEvent sendEmail(FinanceStockAlert alert, String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.SKIPPED, "Email address is blank", ""));
        }
        if (!props.emailProviderConfigured()) {
            return save(systemEvent(
                    alert,
                    FinanceAlertDeliveryChannel.EMAIL,
                    FinanceAlertDeliveryStatus.FAILED,
                    "AWS SES is not configured (enable finance emails, set email-from, aws-region, and IAM/credentials)",
                    ""));
        }
        SesV2Client client = sesClient();
        if (client == null) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.FAILED, "SES client is not available", ""));
        }
        try {
            SendEmailRequest req = SendEmailRequest.builder()
                    .fromEmailAddress(props.emailFrom())
                    .destination(Destination.builder().toAddresses(to.trim()).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder()
                                            .data(subject)
                                            .charset("UTF-8")
                                            .build())
                                    .body(Body.builder()
                                            .text(Content.builder()
                                                    .data(body)
                                                    .charset("UTF-8")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            var resp = client.sendEmail(req);
            String mid = resp.messageId() != null ? resp.messageId() : "";
            return save(systemEvent(
                    alert,
                    FinanceAlertDeliveryChannel.EMAIL,
                    FinanceAlertDeliveryStatus.SENT,
                    "Email sent to " + to.trim() + " via SES",
                    mid));
        } catch (AwsServiceException e) {
            log.warn("Finance alert SES email failed", e);
            return save(systemEvent(
                    alert,
                    FinanceAlertDeliveryChannel.EMAIL,
                    FinanceAlertDeliveryStatus.FAILED,
                    "SES email failed: " + clean(e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage()),
                    ""));
        } catch (Exception e) {
            log.warn("Finance alert SES email failed", e);
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.FAILED, "Email failed: " + clean(e.getMessage()), ""));
        }
    }

    private FinanceAlertEvent sendSms(FinanceStockAlert alert, String to, String body) {
        if (to == null || to.isBlank()) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.SMS, FinanceAlertDeliveryStatus.SKIPPED, "Mobile number is blank", ""));
        }
        if (!props.smsProviderConfigured()) {
            return save(systemEvent(
                    alert,
                    FinanceAlertDeliveryChannel.SMS,
                    FinanceAlertDeliveryStatus.FAILED,
                    "AWS SNS SMS is not configured (enable finance SMS, set aws-region, and IAM/credentials)",
                    ""));
        }
        SnsClient client = snsClient();
        if (client == null) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.SMS, FinanceAlertDeliveryStatus.FAILED, "SNS client is not available", ""));
        }
        try {
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            attrs.put(
                    "AWS.SNS.SMS.SMSType",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(props.snsSmsTypeAttributeValue())
                            .build());
            if (!props.smsSenderId().isBlank()) {
                attrs.put(
                        "AWS.SNS.SMS.SenderID",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(props.smsSenderId())
                                .build());
            }
            PublishRequest req = PublishRequest.builder()
                    .phoneNumber(to.trim())
                    .message(body)
                    .messageAttributes(attrs)
                    .build();
            var resp = client.publish(req);
            String mid = resp.messageId() != null ? resp.messageId() : "";
            return save(systemEvent(
                    alert,
                    FinanceAlertDeliveryChannel.SMS,
                    FinanceAlertDeliveryStatus.SENT,
                    "SMS sent to " + to.trim() + " via SNS",
                    mid));
        } catch (AwsServiceException e) {
            log.warn("Finance alert SNS SMS failed", e);
            return save(systemEvent(
                    alert,
                    FinanceAlertDeliveryChannel.SMS,
                    FinanceAlertDeliveryStatus.FAILED,
                    "SNS SMS failed: " + clean(e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage()),
                    ""));
        } catch (Exception e) {
            log.warn("Finance alert SNS SMS failed", e);
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.SMS, FinanceAlertDeliveryStatus.FAILED, "SMS failed: " + clean(e.getMessage()), ""));
        }
    }

    private SesV2Client sesClient() {
        if (!props.emailProviderConfigured()) {
            return null;
        }
        if (sesClient != null) {
            return sesClient;
        }
        synchronized (clientLock) {
            if (sesClient == null) {
                sesClient = SesV2Client.builder().region(Region.of(props.awsRegion())).build();
            }
            return sesClient;
        }
    }

    private SnsClient snsClient() {
        if (!props.smsProviderConfigured()) {
            return null;
        }
        if (snsClient != null) {
            return snsClient;
        }
        synchronized (clientLock) {
            if (snsClient == null) {
                snsClient = SnsClient.builder().region(Region.of(props.awsRegion())).build();
            }
            return snsClient;
        }
    }

    @PreDestroy
    public void closeAwsClients() {
        synchronized (clientLock) {
            if (snsClient != null) {
                snsClient.close();
                snsClient = null;
            }
            if (sesClient != null) {
                sesClient.close();
                sesClient = null;
            }
        }
    }

    private FinanceAlertEvent save(FinanceAlertEvent event) {
        return eventRepository.save(event);
    }

    private FinanceAlertEvent systemEvent(
            FinanceStockAlert alert,
            FinanceAlertDeliveryChannel channel,
            FinanceAlertDeliveryStatus status,
            String message,
            String providerResponse) {
        FinanceAlertEvent e = new FinanceAlertEvent();
        e.setAlertId(alert.getId());
        e.setOwnerUserId(alert.getOwnerUserId());
        e.setSymbol(alert.getSymbol());
        e.setTriggerType(alert.getTriggerType());
        e.setThresholdValue(alert.getThresholdValue());
        e.setObservedPrice(alert.getLastRegularMarketPrice());
        e.setObservedChangePercent(alert.getLastRegularMarketChangePercent());
        e.setChannel(channel);
        e.setStatus(status);
        e.setMessage(message);
        e.setProviderResponse(providerResponse);
        return e;
    }

    private static FinanceStockAlert pseudoAlert(long ownerUserId) {
        FinanceStockAlert a = new FinanceStockAlert();
        a.setOwnerUserId(ownerUserId);
        a.setSymbol("TEST");
        return a;
    }

    /**
     * Finance channels AND member profile notification opt-ins. If the user has no {@link MemberProfile} row yet,
     * opt-in is treated as true so existing installs are not blocked until they save profile preferences.
     */
    private EffectiveNotifyChannels effectiveNotifyChannels(long ownerUserId, boolean financeWantsEmail, boolean financeWantsSms) {
        return memberProfileRepository
                .findByUserId(ownerUserId)
                .map(p -> new EffectiveNotifyChannels(
                        financeWantsEmail && p.isMarketingEmailOptIn(), financeWantsSms && p.isMarketingSmsOptIn()))
                .orElseGet(() -> new EffectiveNotifyChannels(financeWantsEmail, financeWantsSms));
    }

    private boolean memberAllowsEmail(long ownerUserId) {
        return memberProfileRepository
                .findByUserId(ownerUserId)
                .map(MemberProfile::isMarketingEmailOptIn)
                .orElse(true);
    }

    private boolean memberAllowsSms(long ownerUserId) {
        return memberProfileRepository
                .findByUserId(ownerUserId)
                .map(MemberProfile::isMarketingSmsOptIn)
                .orElse(true);
    }

    private record EffectiveNotifyChannels(boolean email, boolean sms) {}

    private static String clean(String s) {
        return s == null ? "unknown" : s.replace('\n', ' ').replace('\r', ' ');
    }
}
