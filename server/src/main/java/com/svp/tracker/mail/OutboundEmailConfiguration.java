package com.svp.tracker.mail;

import com.svp.tracker.config.FinanceAlertProperties;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class OutboundEmailConfiguration {

    @Bean(destroyMethod = "close")
    public OutboundEmailSender outboundEmailSender(
            FinanceAlertProperties props,
            @Autowired(required = false) @Qualifier("trackerSmtpMailSender") JavaMailSender trackerSmtpMailSender) {
        if (props.usesSmtpTransport()) {
            if (trackerSmtpMailSender == null) {
                throw new IllegalStateException(
                        "tracker.finance.alerts.email-transport=smtp but JavaMailSender bean is missing "
                                + "(set smtp-host, smtp-username, smtp-password)");
            }
            return new SmtpOutboundEmailSender(trackerSmtpMailSender);
        }
        return new SesOutboundEmailSender(props);
    }

    /**
     * Dedicated SMTP client for tracker outbound mail when {@code email-transport=smtp}. Not used for SES deployments.
     */
    @Bean(name = "trackerSmtpMailSender")
    @ConditionalOnProperty(
            prefix = "tracker.finance.alerts",
            name = "email-transport",
            havingValue = "smtp",
            matchIfMissing = false)
    public JavaMailSender trackerSmtpMailSender(FinanceAlertProperties props) {
        String host = props.smtpHost();
        String password = props.smtpPassword();
        String user = props.effectiveSmtpUsername();
        if (host.isBlank() || password.isBlank() || user.isBlank()) {
            throw new IllegalStateException(
                    "email-transport=smtp requires tracker.finance.alerts.smtp-host, smtp-password, "
                            + "and smtp-username or email-from");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(props.smtpPort());
        sender.setUsername(user);
        sender.setPassword(password);
        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        int port = props.smtpPort();
        if (port == 465) {
            // Implicit TLS (SMTPS); STARTTLS is for submission port 587.
            p.put("mail.smtp.ssl.enable", "true");
            p.put("mail.smtp.starttls.enable", "false");
            p.put("mail.smtp.starttls.required", "false");
        } else {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }
}
