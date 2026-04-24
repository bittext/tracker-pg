package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.finance.domain.FinanceAlertDeliveryChannel;
import com.svp.tracker.finance.domain.FinanceAlertDeliveryStatus;
import com.svp.tracker.finance.domain.FinanceAlertEvent;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.FinanceStockAlert;
import com.svp.tracker.finance.repository.FinanceAlertEventRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceAlertDispatchService {

    private final FinanceAlertProperties props;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final FinanceAlertEventRepository eventRepository;
    private final RestClient restClient = RestClient.create();

    public List<FinanceAlertEvent> dispatchTriggeredAlert(
            FinanceStockAlert alert,
            FinanceNotificationSettings settings,
            String subject,
            String body) {
        List<FinanceAlertEvent> events = new ArrayList<>();
        if (settings == null || (!settings.isEmailEnabled() && !settings.isSmsEnabled())) {
            events.add(save(systemEvent(
                    alert, FinanceAlertDeliveryChannel.SYSTEM, FinanceAlertDeliveryStatus.SKIPPED, "No notification channels enabled", "")));
            return events;
        }
        if (settings.isEmailEnabled()) {
            events.add(sendEmail(alert, settings.getEmailAddress(), subject, body));
        }
        if (settings.isSmsEnabled()) {
            events.add(sendSms(alert, settings.getMobileE164(), body));
        }
        return events;
    }

    public FinanceAlertEvent testEmail(long ownerUserId, String emailAddress) {
        FinanceStockAlert pseudo = pseudoAlert(ownerUserId);
        return sendEmail(
                pseudo,
                emailAddress,
                "Tracker finance alert test",
                "This is a Tracker finance alert test email. Your alert email channel is configured.");
    }

    public FinanceAlertEvent testSms(long ownerUserId, String mobileE164) {
        FinanceStockAlert pseudo = pseudoAlert(ownerUserId);
        return sendSms(
                pseudo,
                mobileE164,
                "Tracker finance alert test: your SMS channel is configured.");
    }

    private FinanceAlertEvent sendEmail(FinanceStockAlert alert, String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.SKIPPED, "Email address is blank", ""));
        }
        if (!props.emailProviderConfigured()) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.FAILED, "Email provider is not configured", ""));
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.FAILED, "JavaMailSender is not available", ""));
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.emailFrom());
            msg.setTo(to.trim());
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.SENT, "Email sent to " + to.trim(), ""));
        } catch (Exception e) {
            log.warn("Finance alert email failed", e);
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.EMAIL, FinanceAlertDeliveryStatus.FAILED, "Email failed: " + clean(e.getMessage()), ""));
        }
    }

    private FinanceAlertEvent sendSms(FinanceStockAlert alert, String to, String body) {
        if (to == null || to.isBlank()) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.SMS, FinanceAlertDeliveryStatus.SKIPPED, "Mobile number is blank", ""));
        }
        if (!props.smsProviderConfigured()) {
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.SMS, FinanceAlertDeliveryStatus.FAILED, "Twilio SMS provider is not configured", ""));
        }
        try {
            String auth = Base64.getEncoder()
                    .encodeToString((props.twilioAccountSid() + ":" + props.twilioAuthToken())
                            .getBytes(StandardCharsets.UTF_8));
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("To", to.trim());
            form.add("From", props.twilioFromNumber());
            form.add("Body", body);
            restClient
                    .post()
                    .uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", props.twilioAccountSid())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.SMS, FinanceAlertDeliveryStatus.SENT, "SMS sent to " + to.trim(), ""));
        } catch (Exception e) {
            log.warn("Finance alert SMS failed", e);
            return save(systemEvent(alert, FinanceAlertDeliveryChannel.SMS, FinanceAlertDeliveryStatus.FAILED, "SMS failed: " + clean(e.getMessage()), ""));
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

    private static String clean(String s) {
        return s == null ? "unknown" : s.replace('\n', ' ').replace('\r', ' ');
    }
}
