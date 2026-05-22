package com.svp.tracker.reportcal.repository;

import com.svp.tracker.reportcal.domain.ReportCalendarAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportCalendarAttachmentRepository extends JpaRepository<ReportCalendarAttachment, Long> {

    @Query("SELECT a FROM ReportCalendarAttachment a JOIN FETCH a.entry WHERE a.id = :id")
    Optional<ReportCalendarAttachment> findByIdWithEntry(@Param("id") long id);
}
