package com.svp.tracker.reportcal.repository;

import com.svp.tracker.reportcal.domain.ReportCalendarEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportCalendarEntryRepository extends JpaRepository<ReportCalendarEntry, Long> {

    @Query("SELECT DISTINCT e FROM ReportCalendarEntry e LEFT JOIN FETCH e.attachments WHERE e.id = :id")
    Optional<ReportCalendarEntry> findByIdWithAttachments(@Param("id") long id);

    @Query(
            """
            SELECT DISTINCT e FROM ReportCalendarEntry e LEFT JOIN FETCH e.attachments
            WHERE e.ownerUserId = :ownerUserId AND e.calendarType = :type
              AND e.entryDate BETWEEN :from AND :to
            ORDER BY e.entryDate ASC, e.id ASC
            """)
    List<ReportCalendarEntry> findByOwnerUserIdAndCalendarTypeAndEntryDateBetweenWithAttachments(
            @Param("ownerUserId") long ownerUserId,
            @Param("type") String type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            """
            SELECT DISTINCT e FROM ReportCalendarEntry e LEFT JOIN FETCH e.attachments
            WHERE e.ownerUserId = :ownerUserId AND e.entryDate BETWEEN :from AND :to
            ORDER BY e.entryDate ASC, e.calendarType ASC, e.id ASC
            """)
    List<ReportCalendarEntry> findByOwnerUserIdAndEntryDateBetweenWithAttachments(
            @Param("ownerUserId") long ownerUserId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<ReportCalendarEntry> findByOwnerUserIdAndCalendarTypeAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            long ownerUserId, String calendarType, LocalDate from, LocalDate to);

    List<ReportCalendarEntry> findByOwnerUserIdAndEntryDateBetweenOrderByEntryDateAscCalendarTypeAscIdAsc(
            long ownerUserId, LocalDate from, LocalDate to);

    boolean existsByOwnerUserIdAndCalendarType(long ownerUserId, String calendarType);
}
