package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "robinhood_rh_supplemental_cash_flow")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodRhSupplementalCashFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "direction", nullable = false, length = 3)
    private String direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "flow_category", nullable = false, length = 32)
    private String flowCategory;

    @Column(name = "trans_code", length = 64)
    private String transCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "Config";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
