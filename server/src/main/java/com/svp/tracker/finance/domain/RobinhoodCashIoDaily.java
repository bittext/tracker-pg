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
@Table(name = "robinhood_cash_io_daily")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodCashIoDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "day_inputs", nullable = false, precision = 19, scale = 2)
    private BigDecimal dayInputs;

    @Column(name = "day_outputs", nullable = false, precision = 19, scale = 2)
    private BigDecimal dayOutputs;

    @Column(name = "day_credits", nullable = false, precision = 19, scale = 2)
    private BigDecimal dayCredits;

    @Column(name = "day_debits", nullable = false, precision = 19, scale = 2)
    private BigDecimal dayDebits;

    @Column(name = "ytd_inputs", nullable = false, precision = 19, scale = 2)
    private BigDecimal ytdInputs;

    @Column(name = "ytd_outputs", nullable = false, precision = 19, scale = 2)
    private BigDecimal ytdOutputs;

    @Column(name = "ytd_credits", nullable = false, precision = 19, scale = 2)
    private BigDecimal ytdCredits;

    @Column(name = "ytd_debits", nullable = false, precision = 19, scale = 2)
    private BigDecimal ytdDebits;

    @Column(name = "adjusted_now", nullable = false, precision = 19, scale = 2)
    private BigDecimal adjustedNow;

    @Column(name = "live_value", precision = 19, scale = 2)
    private BigDecimal liveValue;

    @Column(name = "live_accounts_json", nullable = false, columnDefinition = "TEXT")
    private String liveAccountsJson;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;
}
