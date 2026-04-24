package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceNotificationSettingsRepository extends JpaRepository<FinanceNotificationSettings, Long> {

    Optional<FinanceNotificationSettings> findByOwnerUserId(Long ownerUserId);
}
