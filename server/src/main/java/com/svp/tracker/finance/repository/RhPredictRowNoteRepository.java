package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhPredictRowNote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhPredictRowNoteRepository extends JpaRepository<RhPredictRowNote, Long> {

    List<RhPredictRowNote> findByOwnerUserId(long ownerUserId);

    Optional<RhPredictRowNote> findByOwnerUserIdAndNoteKey(long ownerUserId, String noteKey);
}
