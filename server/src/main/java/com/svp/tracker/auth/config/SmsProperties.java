package com.svp.tracker.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.auth.sms")
public record SmsProperties(
        boolean enabled,
        String provider,
        /**
         * AWS region for {@code provider=sns} (for example {@code us-east-1}). Same credential chain as other AWS SDK
         * usage.
         */
        String awsRegion,
        /**
         * SNS direct SMS type: {@code TRANSACTIONAL} or {@code PROMOTIONAL} (case-insensitive; default transactional).
         */
        String smsSmsType,
        /** Optional {@code AWS.SNS.SMS.SenderID} (supported regions only). */
        String smsSenderId) {

    public SmsProperties {
        if (provider == null || provider.isBlank()) {
            provider = "log";
        } else {
            provider = provider.trim().toLowerCase();
        }
        if (awsRegion == null) {
            awsRegion = "";
        } else {
            awsRegion = awsRegion.trim();
        }
        if (smsSmsType == null || smsSmsType.isBlank()) {
            smsSmsType = "TRANSACTIONAL";
        } else {
            String t = smsSmsType.trim().toUpperCase();
            smsSmsType = ("PROMOTIONAL".equals(t) || "TRANSACTIONAL".equals(t)) ? t : "TRANSACTIONAL";
        }
        if (smsSenderId == null) {
            smsSenderId = "";
        } else {
            smsSenderId = smsSenderId.trim();
        }
    }

    /** For SNS message attribute {@code AWS.SNS.SMS.SMSType}. */
    public String snsSmsTypeAttributeValue() {
        return "PROMOTIONAL".equals(smsSmsType) ? "Promotional" : "Transactional";
    }

    public boolean snsFullyConfigured() {
        return "sns".equals(provider) && enabled && !awsRegion.isBlank();
    }
}
