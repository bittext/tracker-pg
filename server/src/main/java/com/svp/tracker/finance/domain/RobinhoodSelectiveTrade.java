package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "robinhood_selective_trade")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodSelectiveTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "symbol", length = 32)
    private String symbol;

    /** WORKED | DIDNT | MIXED */
    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "account_suffix", length = 8)
    private String accountSuffix;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
