package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.WebProperties;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.RobinhoodAgenticApprovalNotification;
import com.svp.tracker.finance.domain.RobinhoodAgenticOrder;
import com.svp.tracker.finance.repository.RobinhoodAgenticApprovalNotificationRepository;
import com.svp.tracker.mail.OutboundEmailSender;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticApprovalNotificationService {

    private final FinanceAlertProperties alertProps;
    private final WebProperties webProperties;
    private final OutboundEmailSender outboundEmailSender;
    private final FinanceNotificationSettingsService notificationSettingsService;
    private final RobinhoodAgenticAdminDefaultsService adminDefaultsService;
    private final RobinhoodAgenticApprovalNotificationRepository notificationRepository;

    private final Object clientLock = new Object();
    private volatile SnsClient snsClient;

    /** Called after commit when an order is pending approval. */
    @Transactional
    public void notifyPendingApproval(RobinhoodAgenticOrder order) {
        if (order == null || !"pending_approval".equals(order.getStatus())) {
            return;
        }
        long uid = order.getOwnerUserId();
        FinanceNotificationSettings settings = notificationSettingsService.findOrEmpty(uid);
        String subject = buildSubject(order);
        String body = buildBody(order);
        String sms = buildSms(order);

        if (adminDefaultsService.isApprovalAlertEmailEnabled() && settings.isEmailEnabled()) {
            sendEmail(uid, order.getId(), settings.getEmailAddress(), subject, body);
        } else if (adminDefaultsService.isApprovalAlertEmailEnabled()) {
            audit(uid, order.getId(), "EMAIL", "SKIPPED", mask(settings.getEmailAddress()), "Email channel disabled in Finance notifications");
        }

        if (adminDefaultsService.isApprovalAlertSmsEnabled() && settings.isSmsEnabled()) {
            sendSms(uid, order.getId(), settings.getMobileE164(), sms);
        } else if (adminDefaultsService.isApprovalAlertSmsEnabled()) {
            audit(uid, order.getId(), "SMS", "SKIPPED", maskPhone(settings.getMobileE164()), "SMS channel disabled in Finance notifications");
        }
    }

    @Transactional(readOnly = true)
    public List<RobinhoodAgenticApprovalNotification> recentNotifications() {
        return notificationRepository.findTop30ByOrderByCreatedAtDesc();
    }

    private void sendEmail(long uid, long orderId, String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            audit(uid, orderId, "EMAIL", "SKIPPED", "", "Email address is blank — configure Admin → Finance → Notifications");
            return;
        }
        if (!alertProps.emailProviderConfigured()) {
            audit(uid, orderId, "EMAIL", "FAILED", mask(to), "Outbound email not configured");
            return;
        }
        OutboundEmailSender.SendOutcome outcome =
                outboundEmailSender.sendPlainText(alertProps.emailFrom(), List.of(to.trim()), subject, body, null);
        if (!outcome.success()) {
            audit(uid, orderId, "EMAIL", "FAILED", mask(to), outcome.errorDetail());
            return;
        }
        audit(uid, orderId, "EMAIL", "SENT", mask(to), "Approval alert email sent");
    }

    private void sendSms(long uid, long orderId, String to, String body) {
        if (to == null || to.isBlank()) {
            audit(uid, orderId, "SMS", "SKIPPED", "", "Mobile E.164 is blank — configure Admin → Finance → Notifications");
            return;
        }
        if (!alertProps.smsProviderConfigured()) {
            audit(uid, orderId, "SMS", "FAILED", maskPhone(to), "SNS SMS not configured");
            return;
        }
        SnsClient client = snsClient();
        if (client == null) {
            audit(uid, orderId, "SMS", "FAILED", maskPhone(to), "SNS client unavailable");
            return;
        }
        try {
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            attrs.put(
                    "AWS.SNS.SMS.SMSType",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(alertProps.snsSmsTypeAttributeValue())
                            .build());
            if (!alertProps.smsSenderId().isBlank()) {
                attrs.put(
                        "AWS.SNS.SMS.SenderID",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(alertProps.smsSenderId())
                                .build());
            }
            client.publish(PublishRequest.builder()
                    .phoneNumber(to.trim())
                    .message(body)
                    .messageAttributes(attrs)
                    .build());
            audit(uid, orderId, "SMS", "SENT", maskPhone(to), "Approval alert SMS sent");
        } catch (AwsServiceException e) {
            log.warn("Agentic approval SMS failed for order {}", orderId, e);
            audit(uid, orderId, "SMS", "FAILED", maskPhone(to), clean(e.getMessage()));
        } catch (Exception e) {
            log.warn("Agentic approval SMS failed for order {}", orderId, e);
            audit(uid, orderId, "SMS", "FAILED", maskPhone(to), clean(e.getMessage()));
        }
    }

    private SnsClient snsClient() {
        if (!alertProps.smsProviderConfigured()) {
            return null;
        }
        if (snsClient != null) {
            return snsClient;
        }
        synchronized (clientLock) {
            if (snsClient == null) {
                snsClient = SnsClient.builder().region(Region.of(alertProps.awsRegion())).build();
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
        }
    }

    private void audit(long uid, long orderId, String channel, String status, String dest, String detail) {
        RobinhoodAgenticApprovalNotification n = new RobinhoodAgenticApprovalNotification();
        n.setOwnerUserId(uid);
        n.setOrderId(orderId);
        n.setChannel(channel);
        n.setStatus(status);
        n.setDestinationMasked(dest);
        n.setDetail(truncate(detail, 500));
        notificationRepository.save(n);
    }

    private String buildSubject(RobinhoodAgenticOrder order) {
        return "Robinhood Agentic order approval needed: "
                + order.getSide().toUpperCase()
                + " "
                + order.getSymbol();
    }

    private String buildBody(RobinhoodAgenticOrder order) {
        String url = webProperties.publicAppUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("A Robinhood Agentic order requires your approval.\n\n");
        sb.append("Order #").append(order.getId()).append('\n');
        sb.append("Source: ").append(order.getSource()).append('\n');
        sb.append("Symbol: ").append(order.getSymbol()).append('\n');
        sb.append("Side: ").append(order.getSide()).append('\n');
        if (order.getQuantity() != null) {
            sb.append("Quantity: ").append(order.getQuantity()).append('\n');
        }
        if (order.getEstimatedNotional() != null) {
            sb.append("Est. notional: $").append(order.getEstimatedNotional()).append('\n');
        }
        if (order.getAutoSignalJson() != null && !order.getAutoSignalJson().isBlank()) {
            sb.append("Signal: ").append(truncate(order.getAutoSignalJson(), 300)).append('\n');
        }
        sb.append("\nApprove in the app: ").append(url).append("/finance\n");
        sb.append("Or Admin → Finance → Trading → Robinhood Agentic tracker.\n");
        return sb.toString();
    }

    private String buildSms(RobinhoodAgenticOrder order) {
        String url = webProperties.publicAppUrl();
        return "Agentic "
                + order.getSide().toUpperCase()
                + " "
                + order.getSymbol()
                + " order #"
                + order.getId()
                + " needs approval. "
                + url
                + "/finance";
    }

    private static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String clean(String s) {
        return s == null ? "unknown" : s.replace('\n', ' ').replace('\r', ' ');
    }
}
