package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.TradingJournalAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingJournalAttachmentRepository extends JpaRepository<TradingJournalAttachment, Long> {

    long countByEntryIdAndOwnerUserId(long entryId, long ownerUserId);

    List<TradingJournalAttachment> findByEntryIdAndOwnerUserIdOrderByCreatedAtDesc(long entryId, long ownerUserId);

    Optional<TradingJournalAttachment> findByIdAndOwnerUserId(long id, long ownerUserId);

    void deleteByEntryIdAndOwnerUserId(long entryId, long ownerUserId);
}
