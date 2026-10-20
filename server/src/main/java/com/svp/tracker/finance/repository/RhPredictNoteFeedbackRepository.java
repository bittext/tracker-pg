package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhPredictNoteFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhPredictNoteFeedbackRepository extends JpaRepository<RhPredictNoteFeedback, Long> {

    Optional<RhPredictNoteFeedback> findByOwnerUserId(long ownerUserId);
}
