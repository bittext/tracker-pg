package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceCompanyFinancialsSearch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceCompanyFinancialsSearchRepository
        extends JpaRepository<FinanceCompanyFinancialsSearch, Long> {

    Optional<FinanceCompanyFinancialsSearch> findByOwnerUserIdAndSymbol(Long ownerUserId, String symbol);

    Optional<FinanceCompanyFinancialsSearch> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<FinanceCompanyFinancialsSearch> findByOwnerUserIdOrderBySearchedAtDesc(Long ownerUserId, Limit limit);
}
