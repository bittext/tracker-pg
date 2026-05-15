package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementDocumentRepository extends JpaRepository<ManagementDocument, Long> {

    List<ManagementDocument> findByOwnerUserIdOrderByCreatedAtDesc(long ownerUserId);

    Optional<ManagementDocument> findByIdAndOwnerUserId(long id, long ownerUserId);
}
