package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementWorkLogAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementWorkLogAttachmentRepository extends JpaRepository<ManagementWorkLogAttachment, Long> {

    @Query("SELECT DISTINCT a FROM ManagementWorkLogAttachment a JOIN FETCH a.entry WHERE a.id = :id")
    Optional<ManagementWorkLogAttachment> findByIdWithEntry(@Param("id") long id);
}
