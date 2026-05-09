package com.svp.tracker.mail;

import java.util.List;

/** Outbound transactional / alert email (SES or SMTP). */
public interface OutboundEmailSender extends AutoCloseable {

    @Override
    default void close() {}

    /**
     * Send a UTF-8 plain-text message.
     *
     * @param replyTo when non-null and non-empty, first address used as Reply-To (e.g. member feedback).
     */
    SendOutcome sendPlainText(String from, List<String> to, String subject, String textBody, List<String> replyTo);

    record SendOutcome(boolean success, String providerMessageId, String errorDetail) {
        public static SendOutcome ok(String providerMessageId) {
            return new SendOutcome(true, providerMessageId != null ? providerMessageId : "", "");
        }

        public static SendOutcome fail(String detail) {
            return new SendOutcome(false, "", detail != null ? detail : "unknown");
        }
    }
}
