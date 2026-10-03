package com.svp.tracker.management.domain;

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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "management_auto_payments")
@Getter
@Setter
@NoArgsConstructor
public class ManagementAutoPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "desk", nullable = false, length = 16)
    private ManagementDesk desk = ManagementDesk.LIFE;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payee = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String category = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private ManagementAutoPaymentMethod paymentMethod = ManagementAutoPaymentMethod.ACH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ManagementAutoPaymentFrequency frequency = ManagementAutoPaymentFrequency.MONTHLY;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "next_payment_on")
    private LocalDate nextPaymentOn;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ManagementAutoPaymentStatus status = ManagementAutoPaymentStatus.ACTIVE;

    @Column(name = "funding_account", nullable = false, columnDefinition = "TEXT")
    private String fundingAccount = "";

    @Column(name = "confirmation_ref", nullable = false, columnDefinition = "TEXT")
    private String confirmationRef = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String website = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
