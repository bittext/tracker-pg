package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
}
