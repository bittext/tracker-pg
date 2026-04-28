package com.svp.tracker.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(
        prefix = "tracker.auth.sms",
        name = "provider",
        havingValue = "log",
        matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void sendOtp(String phoneE164, String code) {
        log.info("SMS OTP (log provider) to {}: {}", phoneE164, code);
    }
}
