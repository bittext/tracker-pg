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
@Table(name = "robinhood_agentic_synced_orders")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticSyncedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_number", nullable = false, columnDefinition = "TEXT")
    private String accountNumber;

    @Column(name = "robinhood_order_id", nullable = false, columnDefinition = "TEXT")
    private String robinhoodOrderId;

    @Column(name = "symbol", nullable = false, columnDefinition = "TEXT")
    private String symbol;

    @Column(name = "side", length = 8)
    private String side;

    @Column(name = "order_type", length = 16)
    private String orderType;

    @Column(name = "quantity", precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 19, scale = 6)
    private BigDecimal limitPrice;

    @Column(name = "average_price", precision = 19, scale = 6)
    private BigDecimal averagePrice;

    @Column(name = "state", length = 32)
    private String state;

    @Column(name = "created_at_rh")
    private Instant createdAtRh;

    @Column(name = "updated_at_rh")
    private Instant updatedAtRh;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
