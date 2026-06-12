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
@Table(name = "robinhood_agentic_positions")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_number", nullable = false, columnDefinition = "TEXT")
    private String accountNumber;

    @Column(name = "symbol", nullable = false, columnDefinition = "TEXT")
    private String symbol;

    @Column(name = "quantity", precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "average_buy_price", precision = 19, scale = 6)
    private BigDecimal averageBuyPrice;

    @Column(name = "market_value", precision = 19, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
