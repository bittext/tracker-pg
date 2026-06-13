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
@Table(name = "robinhood_agentic_orders")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "symbol", nullable = false, columnDefinition = "TEXT")
    private String symbol;

    @Column(name = "side", nullable = false, length = 8)
    private String side;

    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;

    @Column(name = "quantity", precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "limit_price", precision = 19, scale = 6)
    private BigDecimal limitPrice;

    @Column(name = "time_in_force", length = 16)
    private String timeInForce;

    @Column(name = "account_number", columnDefinition = "TEXT")
    private String accountNumber;

    @Column(name = "estimated_notional", precision = 19, scale = 2)
    private BigDecimal estimatedNotional;

    @Column(name = "review_json", columnDefinition = "TEXT")
    private String reviewJson;

    @Column(name = "place_json", columnDefinition = "TEXT")
    private String placeJson;

    @Column(name = "robinhood_order_id", columnDefinition = "TEXT")
    private String robinhoodOrderId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "source", nullable = false, length = 16)
    private String source = "manual";

    @Column(name = "auto_signal_json", columnDefinition = "TEXT")
    private String autoSignalJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
