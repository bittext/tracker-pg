package com.svp.tracker.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
@RequiredArgsConstructor
public final class SmtpOutboundEmailSender implements OutboundEmailSender {

    private final JavaMailSender mailSender;

    @Override
    public SendOutcome sendPlainText(
            String from, List<String> to, String subject, String textBody, List<String> replyTo) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(textBody, false);
            if (replyTo != null && !replyTo.isEmpty()) {
                String rt = replyTo.get(0).trim();
                if (!rt.isEmpty()) {
                    helper.setReplyTo(rt);
                }
            }
            mailSender.send(mime);
            return SendOutcome.ok("");
        } catch (Exception e) {
            log.warn("SMTP send failed", e);
            String detail = smtpFailureDetail(e);
            return SendOutcome.fail(detail);
        }
    }

    /** Prefer nested causes (Jakarta Mail often wraps {@link AuthenticationFailedException}). */
    private static String smtpFailureDetail(Throwable e) {
        String chain = joinExceptionMessages(e);
        if (chain.isBlank()) {
            chain = "SMTP error";
        }
        if (isAuthFailure(e)) {
            if (looksLikeGoogleSmtpRefusal(e)) {
                chain =
                        chain
                                + " Gmail/Google Workspace: turn on 2-Step Verification and use an App Password for"
                                + " TRACKER_FINANCE_ALERTS_SMTP_PASSWORD (normal account passwords are rejected).";
            } else {
                chain =
                        chain
                                + " Verify SMTP username/password and host/port; in .env files quote values containing"
                                + " $ or #.";
            }
        }
        return chain;
    }

    private static String joinExceptionMessages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 800; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null || m.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" — ");
            }
            sb.append(m.trim());
        }
        return sb.toString();
    }

    private static boolean isAuthFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException) {
                return true;
            }
            String m = t.getMessage();
            if (m != null) {
                String low = m.toLowerCase(Locale.ROOT);
                if (low.contains("authentication failed")
                        || low.contains("username and password not accepted")
                        || low.contains("535")
                        || low.contains("534")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Gmail often returns 535-5.7.8 and/or this phrase; avoids blaming Google for every SMTP auth failure. */
    private static boolean looksLikeGoogleSmtpRefusal(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) {
                continue;
            }
            String low = m.toLowerCase(Locale.ROOT);
            if (low.contains("535-5.7.8") || low.contains("username and password not accepted")) {
                return true;
            }
        }
        return false;
    }
}
