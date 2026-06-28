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
@Table(name = "robinhood_rh_account_starting_balance")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodRhAccountStartingBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix;

    /** Portfolio total at RH Accounts Track cutoff for this •••• suffix. */
    @Column(name = "starting_total_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal startingTotalValue = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
