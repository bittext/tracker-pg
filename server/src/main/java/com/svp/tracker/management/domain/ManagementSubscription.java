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
@Table(name = "management_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class ManagementSubscription {

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
    private String vendor = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String category = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String plan = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 16)
    private ManagementSubscriptionBillingCycle billingCycle = ManagementSubscriptionBillingCycle.ANNUAL;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "enrolled_on")
    private LocalDate enrolledOn;

    @Column(name = "renews_on")
    private LocalDate renewsOn;

    @Column(name = "trial_ends_on")
    private LocalDate trialEndsOn;

    @Column(name = "cancelled_on")
    private LocalDate cancelledOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ManagementSubscriptionStatus status = ManagementSubscriptionStatus.ACTIVE;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String website = "";

    @Column(name = "account_email", nullable = false, columnDefinition = "TEXT")
    private String accountEmail = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
