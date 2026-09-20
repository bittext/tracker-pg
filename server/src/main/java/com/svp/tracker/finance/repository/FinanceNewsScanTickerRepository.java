package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceNewsScanTicker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceNewsScanTickerRepository extends JpaRepository<FinanceNewsScanTicker, Long> {

    List<FinanceNewsScanTicker> findByOwnerUserIdOrderBySortOrderAscIdAsc(Long ownerUserId);

    void deleteByOwnerUserId(Long ownerUserId);
}
