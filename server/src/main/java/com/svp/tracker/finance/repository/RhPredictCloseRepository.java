package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhPredictClose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhPredictCloseRepository extends JpaRepository<RhPredictClose, Long> {
    List<RhPredictClose> findByOwnerUserIdOrderByClosedAtDescIdDesc(long ownerUserId);

    Optional<RhPredictClose> findByOwnerUserIdAndFingerprint(long ownerUserId, String fingerprint);

    Optional<RhPredictClose> findByIdAndOwnerUserId(long id, long ownerUserId);
}
