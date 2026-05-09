package com.svp.tracker.mail;

import com.svp.tracker.config.FinanceAlertProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Slf4j
public final class SesOutboundEmailSender implements OutboundEmailSender, AutoCloseable {

    private final FinanceAlertProperties props;
    private final Object lock = new Object();
    private volatile SesV2Client client;

    public SesOutboundEmailSender(FinanceAlertProperties props) {
        this.props = props;
    }

    @Override
    public SendOutcome sendPlainText(
            String from, List<String> to, String subject, String textBody, List<String> replyTo) {
        SesV2Client c = client();
        if (c == null) {
            return SendOutcome.fail("SES client is not available");
        }
        try {
            var reqBuilder = SendEmailRequest.builder()
                    .fromEmailAddress(from)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder()
                                            .data(subject)
                                            .charset("UTF-8")
                                            .build())
                                    .body(Body.builder()
                                            .text(Content.builder()
                                                    .data(textBody)
                                                    .charset("UTF-8")
                                                    .build())
                                            .build())
                                    .build())
                            .build());
            if (replyTo != null && !replyTo.isEmpty()) {
                reqBuilder.replyToAddresses(replyTo);
            }
            var resp = c.sendEmail(reqBuilder.build());
            String mid = resp.messageId() != null ? resp.messageId() : "";
            return SendOutcome.ok(mid);
        } catch (AwsServiceException e) {
            String msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            log.warn("SES send failed: {}", msg);
            return SendOutcome.fail(msg != null ? msg : "SES error");
        } catch (Exception e) {
            log.warn("SES send failed", e);
            return SendOutcome.fail(e.getMessage() != null ? e.getMessage() : "SES error");
        }
    }

    private SesV2Client client() {
        synchronized (lock) {
            if (client == null) {
                client = SesV2Client.builder().region(Region.of(props.awsRegion())).build();
            }
            return client;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (client != null) {
                client.close();
                client = null;
            }
        }
    }
}
