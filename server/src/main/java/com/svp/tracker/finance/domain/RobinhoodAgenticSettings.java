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
@Table(name = "robinhood_agentic_settings")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval = true;

    @Column(name = "max_order_notional", precision = 19, scale = 2)
    private BigDecimal maxOrderNotional;

    @Column(name = "allowed_symbols", columnDefinition = "TEXT")
    private String allowedSymbols;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
