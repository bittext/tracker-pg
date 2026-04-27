package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementMonthNote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementMonthNoteRepository extends JpaRepository<ManagementMonthNote, Long> {

    @Query("SELECT DISTINCT n FROM ManagementMonthNote n LEFT JOIN FETCH n.attachments WHERE n.id = :id")
    Optional<ManagementMonthNote> findByIdWithAttachments(@Param("id") long id);

    @Query("SELECT DISTINCT n FROM ManagementMonthNote n LEFT JOIN FETCH n.attachments "
            + "WHERE n.ownerUserId = :ownerId AND n.year = :year ORDER BY n.month ASC, n.id ASC")
    List<ManagementMonthNote> findByOwnerAndYearWithAttachments(
            @Param("ownerId") long ownerId, @Param("year") int year);

    @Query("SELECT DISTINCT n FROM ManagementMonthNote n LEFT JOIN FETCH n.attachments "
            + "WHERE n.ownerUserId = :ownerId AND n.year = :year AND n.month = :month ORDER BY n.id ASC")
    List<ManagementMonthNote> findByOwnerAndYearMonthWithAttachments(
            @Param("ownerId") long ownerId, @Param("year") int year, @Param("month") int month);

    @Query(
            "SELECT n.month, COUNT(n) FROM ManagementMonthNote n "
                    + "WHERE n.ownerUserId = :ownerId AND n.year = :year GROUP BY n.month")
    List<Object[]> countByMonthForYear(@Param("ownerId") long ownerId, @Param("year") int year);
}
