package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ManagementSubscriptionWriteRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 256) String vendor,
        @Size(max = 128) String category,
        @Size(max = 128) String plan,
        @Size(max = 16) String billingCycle,
        BigDecimal amount,
        @Size(max = 8) String currency,
        LocalDate enrolledOn,
        LocalDate renewsOn,
        LocalDate trialEndsOn,
        LocalDate cancelledOn,
        @Size(max = 16) String status,
        Boolean autoRenew,
        @Size(max = 1024) String website,
        @Size(max = 256) String accountEmail,
        @Size(max = 16384) String notes) {}
