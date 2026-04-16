package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.ExerciseDayLog;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseDayLogRepository extends JpaRepository<ExerciseDayLog, Long> {

    List<ExerciseDayLog> findByExerciseIdAndPerformedOnOrderByIdAsc(Long exerciseId, LocalDate performedOn);

    List<ExerciseDayLog> findByPerformedOnBetweenOrderByPerformedOnAscIdAsc(LocalDate from, LocalDate to);

    @Query("select distinct l.performedOn from ExerciseDayLog l where l.performedOn between :from and :to order by l.performedOn")
    List<LocalDate> findDistinctPerformedOnBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    void deleteByExercise_Id(Long exerciseId);
}
