package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rh_individual_margin_alert_event")
@Getter
@Setter
@NoArgsConstructor
public class RhIndividualMarginAlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix = "3370";

    @Column(name = "peek_id")
    private Long peekId;

    @Column(name = "event_kind", nullable = false, length = 32)
    private String eventKind;

    @Column(name = "buffer_percent", precision = 8, scale = 2)
    private BigDecimal bufferPercent;

    @Column(name = "borrow_percent", precision = 8, scale = 2)
    private BigDecimal borrowPercent;

    @Column(name = "email_status", nullable = false, length = 16)
    private String emailStatus;

    @Column(name = "destination_masked", columnDefinition = "TEXT")
    private String destinationMasked;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
