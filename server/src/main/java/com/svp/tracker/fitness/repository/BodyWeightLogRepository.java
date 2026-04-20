package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.BodyWeightLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BodyWeightLogRepository extends JpaRepository<BodyWeightLog, Long> {

    List<BodyWeightLog> findAllByOrderByLoggedOnDesc();

    Optional<BodyWeightLog> findFirstByLoggedOn(LocalDate loggedOn);

    List<BodyWeightLog> findByLoggedOnBetweenOrderByLoggedOnAsc(LocalDate from, LocalDate to);

    List<BodyWeightLog> findByOwnerUserIdOrderByLoggedOnDesc(Long ownerUserId);

    Optional<BodyWeightLog> findFirstByOwnerUserIdAndLoggedOn(Long ownerUserId, LocalDate loggedOn);

    List<BodyWeightLog> findByOwnerUserIdAndLoggedOnBetweenOrderByLoggedOnAsc(
            Long ownerUserId, LocalDate from, LocalDate to);

    @Query("select distinct b.loggedOn from BodyWeightLog b where b.loggedOn between :from and :to order by b.loggedOn")
    List<LocalDate> findDistinctLoggedOnBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(
            "select distinct b.loggedOn from BodyWeightLog b where b.ownerUserId = :ownerUserId and b.loggedOn between :from and :to order by b.loggedOn")
    List<LocalDate> findDistinctLoggedOnBetweenForOwner(
            @Param("ownerUserId") Long ownerUserId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
