package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.Exercise;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByOwnerUserIdOrderByNameAsc(Long ownerUserId);
}
