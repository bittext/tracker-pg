package com.svp.tracker.fitness.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fitness_body_weight")
@Getter
@Setter
@NoArgsConstructor
public class BodyWeightLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDate loggedOn;

    @NotNull
    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 3)
    private BigDecimal weightKg;

    /** Pounds; persisted in {@code weight_lb}. */
    @Column(name = "weight_lb", precision = 8, scale = 3)
    private BigDecimal weightLb;

    private String notes;
}
