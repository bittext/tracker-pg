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
@Table(name = "fitness_habit_streak_mark")
@Getter
@Setter
@NoArgsConstructor
public class FitnessHabitStreakMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "habit_kind", nullable = false, length = 32)
    private String habitKind;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
