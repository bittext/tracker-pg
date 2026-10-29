package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.AppleHealthWorkout;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppleHealthWorkoutRepository extends JpaRepository<AppleHealthWorkout, Long> {

    List<AppleHealthWorkout> findByOwnerUserIdAndDayBetweenOrderByStartedAtDesc(
            long ownerUserId, LocalDate from, LocalDate to);

    @Query("select w.fingerprint from AppleHealthWorkout w where w.ownerUserId = :ownerUserId")
    List<String> findFingerprintsByOwnerUserId(long ownerUserId);

    long countByOwnerUserId(long ownerUserId);

    void deleteByOwnerUserId(long ownerUserId);
}
