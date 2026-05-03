package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementWriteupAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementWriteupAttachmentRepository extends JpaRepository<ManagementWriteupAttachment, Long> {

    @Query("SELECT a FROM ManagementWriteupAttachment a JOIN FETCH a.writeup WHERE a.id = :id")
    Optional<ManagementWriteupAttachment> findByIdWithWriteup(@Param("id") long id);
}
