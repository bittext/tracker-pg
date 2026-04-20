package com.svp.tracker.fitness.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fitness_exercise_day_logs")
@Getter
@Setter
@NoArgsConstructor
public class ExerciseDayLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @NotNull
    @Column(nullable = false)
    private LocalDate performedOn;

    @NotBlank
    @Column(nullable = false, length = 4000)
    private String notes;

    /** Total duration in whole minutes (optional). */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "owner_user_id")
    private Long ownerUserId;
}
