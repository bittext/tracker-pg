package com.svp.tracker.auth.service;

import com.svp.tracker.auth.config.SmsProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
@Primary
@ConditionalOnProperty(prefix = "tracker.auth.sms", name = "provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private final SmsProperties smsProperties;
    private final RestClient restClient = RestClient.create();

    public TwilioSmsSender(SmsProperties smsProperties) {
        this.smsProperties = smsProperties;
    }

    @Override
    public void sendOtp(String phoneE164, String code) {
        if (smsProperties.twilioAccountSid().isBlank()
                || smsProperties.twilioAuthToken().isBlank()
                || smsProperties.twilioFromNumber().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "SMS provider is not fully configured");
        }
        String auth = Base64.getEncoder()
                .encodeToString((smsProperties.twilioAccountSid() + ":" + smsProperties.twilioAuthToken())
                        .getBytes(StandardCharsets.UTF_8));
        String bodyText = "Your Tracker verification code is " + code + ".";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneE164);
        form.add("From", smsProperties.twilioFromNumber());
        form.add("Body", bodyText);
        restClient
                .post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", smsProperties.twilioAccountSid())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
