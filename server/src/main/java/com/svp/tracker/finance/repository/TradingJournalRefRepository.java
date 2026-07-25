package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.TradingJournalRef;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingJournalRefRepository extends JpaRepository<TradingJournalRef, Long> {

    List<TradingJournalRef> findByEntryIdAndOwnerUserIdOrderByCreatedAtDesc(long entryId, long ownerUserId);

    Optional<TradingJournalRef> findByIdAndOwnerUserId(long id, long ownerUserId);

    void deleteByEntryIdAndOwnerUserId(long entryId, long ownerUserId);
}
