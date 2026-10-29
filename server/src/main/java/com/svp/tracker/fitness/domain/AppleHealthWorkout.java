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
@Table(name = "fitness_apple_health_workout")
@Getter
@Setter
@NoArgsConstructor
public class AppleHealthWorkout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 80)
    private String activity;

    @Column(name = "source_name", length = 128)
    private String sourceName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(nullable = false)
    private LocalDate day;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "distance_miles", precision = 8, scale = 3)
    private BigDecimal distanceMiles;

    @Column(name = "energy_kcal", precision = 8, scale = 1)
    private BigDecimal energyKcal;
}
