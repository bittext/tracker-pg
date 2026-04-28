package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.alerts")
public record FinanceAlertProperties(
        boolean evaluationEnabled,
        long pollFixedDelayMs,
        int defaultCooldownMinutes,
        int maxEventsReturned,
        String emailFrom,
        boolean emailEnabled,
        boolean smsEnabled,
        /**
         * AWS region for Amazon SES and SNS (for example {@code us-east-1}). Required on the server when the
         * corresponding channel is enabled; credentials use the default AWS provider chain (env keys, profile,
         * container/instance role).
         */
        String awsRegion,
        /**
         * SNS direct SMS type: {@code TRANSACTIONAL} or {@code PROMOTIONAL} (case-insensitive; default transactional).
         */
        String smsSmsType,
        /**
         * Optional {@code AWS.SNS.SMS.SenderID} message attribute (supported in some regions only; leave blank to omit).
         */
        String smsSenderId) {

    public FinanceAlertProperties {
        if (pollFixedDelayMs < 30_000) {
            pollFixedDelayMs = 300_000;
        }
        if (defaultCooldownMinutes < 0) {
            defaultCooldownMinutes = 1440;
        }
        if (maxEventsReturned < 1) {
            maxEventsReturned = 50;
        }
        if (maxEventsReturned > 500) {
            maxEventsReturned = 500;
        }
        emailFrom = clean(emailFrom);
        awsRegion = clean(awsRegion);
        smsSmsType = normalizeSmsType(smsSmsType);
        smsSenderId = clean(smsSenderId);
    }

    public boolean emailProviderConfigured() {
        return emailEnabled && !emailFrom.isBlank() && !awsRegion.isBlank();
    }

    public boolean smsProviderConfigured() {
        return smsEnabled && !awsRegion.isBlank();
    }

    /** Value for SNS message attribute {@code AWS.SNS.SMS.SMSType}: {@code Transactional} or {@code Promotional}. */
    public String snsSmsTypeAttributeValue() {
        if ("PROMOTIONAL".equalsIgnoreCase(smsSmsType)) {
            return "Promotional";
        }
        return "Transactional";
    }

    private static String normalizeSmsType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "TRANSACTIONAL";
        }
        String t = raw.trim().toUpperCase();
        if (!"TRANSACTIONAL".equals(t) && !"PROMOTIONAL".equals(t)) {
            return "TRANSACTIONAL";
        }
        return t;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
