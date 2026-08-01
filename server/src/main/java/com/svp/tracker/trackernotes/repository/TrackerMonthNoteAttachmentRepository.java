package com.svp.tracker.trackernotes.repository;

import com.svp.tracker.trackernotes.domain.TrackerMonthNoteAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackerMonthNoteAttachmentRepository extends JpaRepository<TrackerMonthNoteAttachment, Long> {

    @Query("SELECT a FROM TrackerMonthNoteAttachment a JOIN FETCH a.note WHERE a.id = :id")
    Optional<TrackerMonthNoteAttachment> findByIdWithNote(@Param("id") long id);
}
