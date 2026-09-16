package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceTaxDeskSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FinanceTaxDeskSettingsRepository extends JpaRepository<FinanceTaxDeskSettings, Long> {

    Optional<FinanceTaxDeskSettings> findByOwnerUserIdAndTaxYear(long ownerUserId, int taxYear);

    @Query("SELECT DISTINCT s.ownerUserId FROM FinanceTaxDeskSettings s")
    List<Long> findDistinctOwnerUserIds();
}
