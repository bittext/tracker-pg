package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementMonthNoteAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementMonthNoteAttachmentRepository extends JpaRepository<ManagementMonthNoteAttachment, Long> {

    @Query("SELECT a FROM ManagementMonthNoteAttachment a JOIN FETCH a.note WHERE a.id = :id")
    Optional<ManagementMonthNoteAttachment> findByIdWithNote(@Param("id") long id);
}
