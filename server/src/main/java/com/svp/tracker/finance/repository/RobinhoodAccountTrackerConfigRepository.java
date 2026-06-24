package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAccountTrackerConfigRepository extends JpaRepository<RobinhoodAccountTrackerConfig, Long> {

    Optional<RobinhoodAccountTrackerConfig> findByOwnerUserId(long ownerUserId);
}
