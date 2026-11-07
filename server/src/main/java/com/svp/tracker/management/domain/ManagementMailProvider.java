package com.svp.tracker.management.domain;

public enum ManagementMailProvider {
    GMAIL("imap.gmail.com", "Gmail"),
    HOTMAIL("outlook.office365.com", "Hotmail"),
    YAHOO("imap.mail.yahoo.com", "Yahoo");

    private final String imapHost;
    private final String label;

    ManagementMailProvider(String imapHost, String label) {
        this.imapHost = imapHost;
        this.label = label;
    }

    public String imapHost() {
        return imapHost;
    }

    public String label() {
        return label;
    }

    public static ManagementMailProvider fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Choose Gmail, Hotmail, or Yahoo.");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Choose Gmail, Hotmail, or Yahoo.");
        }
    }
}
