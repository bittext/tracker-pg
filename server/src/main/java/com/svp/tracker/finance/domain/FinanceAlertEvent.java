package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "finance_alert_events")
@Getter
@Setter
@NoArgsConstructor
public class FinanceAlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long alertId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(length = 48)
    private FinanceStockAlertTriggerType triggerType;

    @Column(precision = 18, scale = 6)
    private BigDecimal thresholdValue;

    @Column(precision = 18, scale = 6)
    private BigDecimal observedPrice;

    @Column(precision = 12, scale = 6)
    private BigDecimal observedChangePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FinanceAlertDeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FinanceAlertDeliveryStatus status;

    @Column(columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "text")
    private String providerResponse;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
