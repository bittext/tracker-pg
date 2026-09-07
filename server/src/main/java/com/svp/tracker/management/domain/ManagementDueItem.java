package com.svp.tracker.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "management_due_items")
@Getter
@Setter
@NoArgsConstructor
public class ManagementDueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ManagementDueSide side = ManagementDueSide.PAYABLE;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String counterparty = "";

    @Column(nullable = false)
    private boolean recurring;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "one_off_date")
    private LocalDate oneOffDate;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn = LocalDate.now();

    @Column(name = "amount_override", precision = 19, scale = 2)
    private BigDecimal amountOverride;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
