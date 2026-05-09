package com.svp.tracker.mail;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
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
            return SendOutcome.fail(e.getMessage() != null ? e.getMessage() : "SMTP error");
        }
    }
}
