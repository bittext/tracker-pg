package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceEntryDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceEntryDocumentRepository extends JpaRepository<FinanceEntryDocument, Long> {

    List<FinanceEntryDocument> findByOwnerUserIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            long ownerUserId, String entityType, long entityId);

    long countByOwnerUserIdAndEntityTypeAndEntityId(long ownerUserId, String entityType, long entityId);

    List<FinanceEntryDocument> findByOwnerUserIdAndEntityTypeAndEntityId(
            long ownerUserId, String entityType, long entityId);

    Optional<FinanceEntryDocument> findByIdAndOwnerUserId(long id, long ownerUserId);
}
