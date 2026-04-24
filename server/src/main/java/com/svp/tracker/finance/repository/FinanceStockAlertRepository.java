package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceStockAlert;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceStockAlertRepository extends JpaRepository<FinanceStockAlert, Long> {

    List<FinanceStockAlert> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<FinanceStockAlert> findByOwnerUserIdAndEnabledTrueOrderBySymbolAscIdAsc(Long ownerUserId);

    List<FinanceStockAlert> findByEnabledTrueOrderBySymbolAscIdAsc();

    Optional<FinanceStockAlert> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<FinanceStockAlert> findByIdIn(Collection<Long> ids);
}
