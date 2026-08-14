package com.svp.tracker.fitness.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fitness_habit_streak_window")
@Getter
@Setter
@NoArgsConstructor
public class FitnessHabitStreakWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "day_count", nullable = false)
    private int dayCount = 50;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
