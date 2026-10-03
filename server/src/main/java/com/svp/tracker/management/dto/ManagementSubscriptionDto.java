package com.svp.tracker.management.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManagementSubscriptionDto(
        long id,
        String name,
        String vendor,
        String category,
        String plan,
        String billingCycle,
        BigDecimal amount,
        String currency,
        LocalDate enrolledOn,
        LocalDate renewsOn,
        LocalDate trialEndsOn,
        LocalDate cancelledOn,
        String status,
        boolean autoRenew,
        String website,
        String accountEmail,
        String notes,
        LocalDate nextRenewalOn,
        Integer daysUntilRenewal,
        boolean refundWindowOpen,
        LocalDate refundWindowEndsOn,
        String createdAt,
        String updatedAt) {}
