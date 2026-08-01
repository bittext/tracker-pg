package com.svp.tracker.life.repository;

import com.svp.tracker.life.domain.LifeMonthNoteAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LifeMonthNoteAttachmentRepository extends JpaRepository<LifeMonthNoteAttachment, Long> {

    @Query("SELECT a FROM LifeMonthNoteAttachment a JOIN FETCH a.note WHERE a.id = :id")
    Optional<LifeMonthNoteAttachment> findByIdWithNote(@Param("id") long id);
}
