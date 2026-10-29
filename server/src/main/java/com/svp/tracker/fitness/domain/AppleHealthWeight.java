package com.svp.tracker.fitness.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "fitness_apple_health_weight")
@Getter
@Setter
@NoArgsConstructor
public class AppleHealthWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "logged_on", nullable = false)
    private LocalDate loggedOn;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "weight_lb", nullable = false, precision = 8, scale = 3)
    private BigDecimal weightLb;

    @Column(name = "source_name", length = 128)
    private String sourceName;
}
