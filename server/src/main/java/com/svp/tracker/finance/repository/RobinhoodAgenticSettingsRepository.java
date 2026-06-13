package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticSettingsRepository extends JpaRepository<RobinhoodAgenticSettings, Long> {
    Optional<RobinhoodAgenticSettings> findByOwnerUserId(long ownerUserId);
}
