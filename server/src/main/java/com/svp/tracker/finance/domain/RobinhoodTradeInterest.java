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
@Table(name = "robinhood_trade_interest")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodTradeInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    /** STOCK | OPTION */
    @Column(name = "instrument_kind", nullable = false, length = 16)
    private String instrumentKind;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    /** When the trader planned to take the trade. */
    @Column(name = "planned_at", nullable = false)
    private Instant plannedAt;

    /** Underlying stock price contemplated at plan time. */
    @Column(name = "underlying_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal underlyingPrice;

    /** Option contract target premium/cost (required when OPTION). */
    @Column(name = "contract_target_price", precision = 19, scale = 4)
    private BigDecimal contractTargetPrice;

    /** Option expiry (required when OPTION). */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** OPEN | TAKEN | PASSED | EXPIRED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
