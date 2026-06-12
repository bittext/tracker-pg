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

    @Column(name = "position_type", nullable = false, length = 16)
    private String positionType = "equity";

    @Column(name = "position_key", nullable = false, columnDefinition = "TEXT")
    private String positionKey;

    @Column(name = "symbol", nullable = false, columnDefinition = "TEXT")
    private String symbol;

    @Column(name = "chain_symbol", columnDefinition = "TEXT")
    private String chainSymbol;

    @Column(name = "option_type", length = 8)
    private String optionType;

    @Column(name = "strike_price", precision = 19, scale = 6)
    private BigDecimal strikePrice;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "quantity", precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "average_buy_price", precision = 19, scale = 6)
    private BigDecimal averageBuyPrice;

    @Column(name = "market_value", precision = 19, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
