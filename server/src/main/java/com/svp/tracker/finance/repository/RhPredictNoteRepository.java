package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhPredictNote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhPredictNoteRepository extends JpaRepository<RhPredictNote, Long> {

    List<RhPredictNote> findByOwnerUserIdOrderByCreatedAtDescIdDesc(long ownerUserId);

    Optional<RhPredictNote> findByIdAndOwnerUserId(long id, long ownerUserId);

    long countByOwnerUserId(long ownerUserId);
}
