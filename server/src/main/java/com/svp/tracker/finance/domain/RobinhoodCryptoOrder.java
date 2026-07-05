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
@Table(name = "robinhood_crypto_orders")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodCryptoOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "trading_pair", nullable = false, length = 32)
    private String tradingPair;

    @Column(name = "side", nullable = false, length = 8)
    private String side;

    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType = "market";

    @Column(name = "quote_amount", precision = 19, scale = 2)
    private BigDecimal quoteAmount;

    @Column(name = "asset_quantity", precision = 19, scale = 8)
    private BigDecimal assetQuantity;

    @Column(name = "estimated_notional", precision = 19, scale = 2)
    private BigDecimal estimatedNotional;

    @Column(name = "client_order_id", length = 64)
    private String clientOrderId;

    @Column(name = "robinhood_order_id", columnDefinition = "TEXT")
    private String robinhoodOrderId;

    @Column(name = "source", nullable = false, length = 16)
    private String source = "manual";

    @Column(name = "auto_signal_json", columnDefinition = "TEXT")
    private String autoSignalJson;

    @Column(name = "place_json", columnDefinition = "TEXT")
    private String placeJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "placed_at")
    private Instant placedAt;
}
