package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_investment_then_now")
@Getter
@Setter
@NoArgsConstructor
public class FinanceInvestmentThenNow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 256)
    private String companyName = "";

    @Column(name = "invested_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal investedAmount;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "price_as_of_date", nullable = false, precision = 18, scale = 6)
    private BigDecimal priceAsOfDate;

    @Column(name = "price_as_of_session", nullable = false)
    private LocalDate priceAsOfSession;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal shares;

    @Column(name = "price_now", nullable = false, precision = 18, scale = 6)
    private BigDecimal priceNow;

    @Column(name = "price_now_session", nullable = false)
    private LocalDate priceNowSession;

    @Column(name = "worth_now", nullable = false, precision = 18, scale = 2)
    private BigDecimal worthNow;

    @Column(name = "gain_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal gainAmount;

    @Column(name = "gain_percent", nullable = false, precision = 12, scale = 4)
    private BigDecimal gainPercent;

    @Column(name = "detail_answer", nullable = false, columnDefinition = "TEXT")
    private String detailAnswer;

    @Column(name = "price_source", nullable = false, length = 64)
    private String priceSource = "nasdaq-chart";

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (computedAt == null) {
            computedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
