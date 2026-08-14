package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.FitnessHabitStreakMark;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FitnessHabitStreakMarkRepository extends JpaRepository<FitnessHabitStreakMark, Long> {

    List<FitnessHabitStreakMark> findByOwnerUserIdAndActivityDateBetween(
            long ownerUserId, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<FitnessHabitStreakMark> findByOwnerUserIdAndHabitKindAndActivityDate(
            long ownerUserId, String habitKind, LocalDate activityDate);
}
