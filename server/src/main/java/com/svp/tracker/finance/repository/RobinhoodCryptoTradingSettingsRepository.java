package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodCryptoTradingSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodCryptoTradingSettingsRepository extends JpaRepository<RobinhoodCryptoTradingSettings, Long> {
    Optional<RobinhoodCryptoTradingSettings> findByOwnerUserId(long ownerUserId);

    List<RobinhoodCryptoTradingSettings> findByAutoTradeEnabledTrueAndAutoTradeKillSwitchFalse();
}
