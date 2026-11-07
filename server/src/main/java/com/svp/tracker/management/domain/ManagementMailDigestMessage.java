package com.svp.tracker.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "management_mail_digest_message")
@Getter
@Setter
@NoArgsConstructor
public class ManagementMailDigestMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "digest_id", nullable = false)
    private Long digestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ManagementMailProvider provider;

    @Column(nullable = false, length = 320)
    private String mailbox = "";

    @Column(name = "from_line", nullable = false, length = 500)
    private String fromLine = "";

    @Column(nullable = false, length = 500)
    private String subject = "";

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snippet = "";
}
