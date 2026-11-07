package com.svp.tracker.management.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/** Reads recent inbox messages over IMAP without marking them read. */
public final class ManagementMailInboxClient {

    static final int MAX_MESSAGES = 40;

    private ManagementMailInboxClient() {}

    public record FetchedMessage(String from, String subject, Instant receivedAt, String snippet) {}

    public static List<FetchedMessage> fetchInbox(String host, String email, String password, Instant since) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.peek", "true");
        props.put("mail.imap.peek", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "25000");
        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        try {
            store.connect(host, email, password);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                Date sinceDate = Date.from(since.minus(Duration.ofDays(1)));
                Message[] found = inbox.search(new ReceivedDateTerm(ComparisonTerm.GE, sinceDate));
                List<Message> recent = new ArrayList<>();
                for (Message message : found) {
                    Instant when = receivedAt(message);
                    if (when != null && !when.isBefore(since)) {
                        recent.add(message);
                    }
                }
                recent.sort(Comparator.comparing((Message message) -> receivedAt(message), Comparator.nullsLast(Comparator.reverseOrder())));
                if (recent.size() > MAX_MESSAGES) {
                    recent = recent.subList(0, MAX_MESSAGES);
                }
                List<FetchedMessage> rows = new ArrayList<>();
                for (Message message : recent) {
                    rows.add(new FetchedMessage(from(message), subject(message), receivedAt(message), snippet(message)));
                }
                return rows;
            } finally {
                inbox.close(false);
            }
        } finally {
            store.close();
        }
    }

    private static Instant receivedAt(Message message) {
        try {
            Date received = message.getReceivedDate();
            if (received == null) {
                received = message.getSentDate();
            }
            return received == null ? null : received.toInstant();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String from(Message message) {
        try {
            if (message.getFrom() == null || message.getFrom().length == 0) {
                return "";
            }
            return clip(message.getFrom()[0].toString(), 240);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String subject(Message message) {
        try {
            return clip(message.getSubject(), 240);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String snippet(Message message) {
        try {
            return clip(plain(message.getContent()), 400);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String plain(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    Object nested = part.getContent();
                    if (nested instanceof String text) {
                        return text;
                    }
                }
            }
            if (multipart.getCount() > 0) {
                Object nested = multipart.getBodyPart(0).getContent();
                if (nested instanceof String text) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max);
    }
}
