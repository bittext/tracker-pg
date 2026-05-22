package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_stock_alerts")
@Getter
@Setter
@NoArgsConstructor
public class FinanceStockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "company_name", length = 256)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private FinanceStockAlertTriggerType triggerType;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FinanceStockAlertRepeatMode repeatMode = FinanceStockAlertRepeatMode.ONCE;

    @Column(nullable = false)
    private int cooldownMinutes = 1440;

    @Column(nullable = false)
    private boolean enabled = true;

    /** When false, a repeating alert waits for price/session to drop below threshold before firing again. */
    @Column(name = "trigger_armed", nullable = false)
    private boolean triggerArmed = true;

    private Instant lastCheckedAt;

    private Instant lastTriggeredAt;

    @Column(precision = 18, scale = 6)
    private BigDecimal lastRegularMarketPrice;

    @Column(precision = 12, scale = 6)
    private BigDecimal lastRegularMarketChangePercent;

    @Column(nullable = false)
    private int fireCount;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        symbol = normalizeSymbol(symbol);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        symbol = normalizeSymbol(symbol);
    }

    private static String normalizeSymbol(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
