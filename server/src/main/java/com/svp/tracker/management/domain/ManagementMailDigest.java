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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "management_mail_digest")
@Getter
@Setter
@NoArgsConstructor
public class ManagementMailDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "digest_on", nullable = false)
    private LocalDate digestOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ManagementMailSlot slot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary = "";

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(nullable = false, length = 16)
    private String status = "OK";
}
