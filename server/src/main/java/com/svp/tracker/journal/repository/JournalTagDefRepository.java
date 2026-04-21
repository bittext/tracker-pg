package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalTagDef;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalTagDefRepository extends JpaRepository<JournalTagDef, Long> {

    List<JournalTagDef> findByOwnerUserIdOrderByNameAsc(long ownerUserId);

    Optional<JournalTagDef> findByOwnerUserIdAndNameIgnoreCase(long ownerUserId, String name);
}
