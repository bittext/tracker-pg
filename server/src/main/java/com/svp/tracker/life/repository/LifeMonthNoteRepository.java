package com.svp.tracker.life.repository;

import com.svp.tracker.life.domain.LifeMonthNote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LifeMonthNoteRepository extends JpaRepository<LifeMonthNote, Long> {

    @Query("SELECT DISTINCT n FROM LifeMonthNote n LEFT JOIN FETCH n.attachments WHERE n.id = :id")
    Optional<LifeMonthNote> findByIdWithAttachments(@Param("id") long id);

    @Query(
            "SELECT DISTINCT n FROM LifeMonthNote n LEFT JOIN FETCH n.attachments "
                    + "WHERE n.ownerUserId = :owner AND n.year = :year ORDER BY n.month ASC, n.id DESC")
    List<LifeMonthNote> findByOwnerAndYearWithAttachments(
            @Param("owner") long owner, @Param("year") int year);

    @Query(
            "SELECT DISTINCT n FROM LifeMonthNote n LEFT JOIN FETCH n.attachments "
                    + "WHERE n.ownerUserId = :owner AND n.year = :year AND n.month = :month ORDER BY n.id DESC")
    List<LifeMonthNote> findByOwnerAndYearMonthWithAttachments(
            @Param("owner") long owner, @Param("year") int year, @Param("month") int month);

    @Query(
            "SELECT n.month, COUNT(n) FROM LifeMonthNote n "
                    + "WHERE n.ownerUserId = :owner AND n.year = :year GROUP BY n.month")
    List<Object[]> countByMonthForYear(@Param("owner") long owner, @Param("year") int year);
}
