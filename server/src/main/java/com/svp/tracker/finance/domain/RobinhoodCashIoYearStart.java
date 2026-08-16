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
@Table(name = "robinhood_cash_io_year_start")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodCashIoYearStart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "starting_cash", nullable = false, precision = 19, scale = 2)
    private BigDecimal startingCash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
