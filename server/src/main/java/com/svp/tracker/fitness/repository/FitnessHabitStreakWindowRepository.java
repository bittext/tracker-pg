package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.FitnessHabitStreakWindow;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FitnessHabitStreakWindowRepository extends JpaRepository<FitnessHabitStreakWindow, Long> {

    Optional<FitnessHabitStreakWindow> findByOwnerUserId(long ownerUserId);
}
