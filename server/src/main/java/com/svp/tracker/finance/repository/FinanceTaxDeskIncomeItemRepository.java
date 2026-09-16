package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceTaxDeskIncomeItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTaxDeskIncomeItemRepository extends JpaRepository<FinanceTaxDeskIncomeItem, Long> {

    List<FinanceTaxDeskIncomeItem> findByOwnerUserIdAndTaxYearOrderBySortOrderAscIdAsc(long ownerUserId, int taxYear);

    Optional<FinanceTaxDeskIncomeItem> findByIdAndOwnerUserId(long id, long ownerUserId);
}
