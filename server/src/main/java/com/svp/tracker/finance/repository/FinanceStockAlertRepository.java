package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceStockAlert;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FinanceStockAlertRepository extends JpaRepository<FinanceStockAlert, Long> {

    List<FinanceStockAlert> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<FinanceStockAlert> findByOwnerUserIdAndEnabledTrueOrderBySymbolAscIdAsc(Long ownerUserId);

    List<FinanceStockAlert> findByEnabledTrueOrderBySymbolAscIdAsc();

    @Query("select distinct a.symbol from FinanceStockAlert a where a.enabled = true and a.symbol is not null and trim(a.symbol) <> ''")
    List<String> findDistinctEnabledSymbols();

    Optional<FinanceStockAlert> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<FinanceStockAlert> findByIdIn(Collection<Long> ids);
}
