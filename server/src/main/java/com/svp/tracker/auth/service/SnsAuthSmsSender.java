package com.svp.tracker.auth.service;

import com.svp.tracker.auth.config.SmsProperties;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * SMS MFA using Amazon SNS direct publish (E.164 destination). Replaces the former Twilio integration.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "tracker.auth.sms", name = "provider", havingValue = "sns")
public class SnsAuthSmsSender implements SmsSender {

    private final SmsProperties smsProperties;
    private final Object clientLock = new Object();
    private volatile SnsClient snsClient;

    public SnsAuthSmsSender(SmsProperties smsProperties) {
        this.smsProperties = smsProperties;
    }

    @Override
    public void sendOtp(String phoneE164, String code) {
        if (!smsProperties.snsFullyConfigured()) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE, "SNS SMS is not fully configured (set tracker.auth.sms.enabled, aws-region)");
        }
        SnsClient client = client();
        String text = "Your Health Tracker & PFM verification code is " + code + ".";
        try {
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            attrs.put(
                    "AWS.SNS.SMS.SMSType",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(smsProperties.snsSmsTypeAttributeValue())
                            .build());
            if (!smsProperties.smsSenderId().isBlank()) {
                attrs.put(
                        "AWS.SNS.SMS.SenderID",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(smsProperties.smsSenderId())
                                .build());
            }
            PublishRequest req = PublishRequest.builder()
                    .phoneNumber(phoneE164)
                    .message(text)
                    .messageAttributes(attrs)
                    .build();
            client.publish(req);
        } catch (AwsServiceException e) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE, "SNS SMS failed: " + (e.getMessage() != null ? e.getMessage() : "error"));
        }
    }

    private SnsClient client() {
        if (snsClient != null) {
            return snsClient;
        }
        synchronized (clientLock) {
            if (snsClient == null) {
                snsClient = SnsClient.builder()
                        .region(Region.of(smsProperties.awsRegion()))
                        .build();
            }
            return snsClient;
        }
    }

    @PreDestroy
    public void close() {
        synchronized (clientLock) {
            if (snsClient != null) {
                snsClient.close();
                snsClient = null;
            }
        }
    }
}
