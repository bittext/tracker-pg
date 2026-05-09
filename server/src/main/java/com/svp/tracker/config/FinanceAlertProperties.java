package com.svp.tracker.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
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
         * Outbound email transport: {@code ses} (Amazon SES, default) or {@code smtp} (Spring JavaMail).
         */
        String emailTransport,
        /** SMTP host when {@code email-transport=smtp} (e.g. {@code smtp.gmail.com}). */
        String smtpHost,
        /** SMTP port when using SMTP (default 587). */
        int smtpPort,
        /** SMTP auth username; if blank, {@link #emailFrom} is used. */
        String smtpUsername,
        /** SMTP password or app password (never commit; use env). */
        String smtpPassword,
        /**
         * AWS region for Amazon SES and SNS (for example {@code us-east-1}). Required on the server when the
         * corresponding channel is enabled; credentials use the default AWS provider chain (env keys, profile,
         * container/instance role). Not required when email uses SMTP only.
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
        emailTransport = normalizeEmailTransport(emailTransport);
        smtpHost = clean(smtpHost);
        if (smtpPort <= 0 || smtpPort > 65535) {
            smtpPort = 587;
        }
        smtpUsername = clean(smtpUsername);
        smtpPassword = clean(smtpPassword);
        awsRegion = clean(awsRegion);
        smsSmsType = normalizeSmsType(smsSmsType);
        smsSenderId = clean(smsSenderId);
    }

    /** True when outbound email uses Spring SMTP instead of Amazon SES. */
    public boolean usesSmtpTransport() {
        return "smtp".equals(emailTransport);
    }

    /** SMTP login: explicit username, or mailbox parsed from {@link #emailFrom} (including {@code Name <a@b>} forms). */
    public String effectiveSmtpUsername() {
        if (!smtpUsername.isBlank()) {
            return smtpUsername;
        }
        return mailboxFromFromHeader(emailFrom);
    }

    /**
     * SMTP AUTH username must be the mailbox (e.g. {@code user@gmail.com}). If {@code email-from} uses a display name,
     * extract the address so providers like Gmail do not reject login.
     */
    private static String mailboxFromFromHeader(String fromHeaderValue) {
        if (fromHeaderValue.isBlank()) {
            return "";
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(fromHeaderValue, false);
            if (parsed.length > 0) {
                String addr = parsed[0].getAddress();
                if (addr != null && !addr.isBlank()) {
                    return addr.trim();
                }
            }
        } catch (AddressException ignored) {
            // fall through
        }
        return fromHeaderValue;
    }

    public boolean emailProviderConfigured() {
        if (!emailEnabled || emailFrom.isBlank()) {
            return false;
        }
        if (usesSmtpTransport()) {
            return !smtpHost.isBlank() && !smtpPassword.isBlank() && !effectiveSmtpUsername().isBlank();
        }
        return !awsRegion.isBlank();
    }

    private static String normalizeEmailTransport(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ses";
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if ("smtp".equals(t)) {
            return "smtp";
        }
        return "ses";
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
