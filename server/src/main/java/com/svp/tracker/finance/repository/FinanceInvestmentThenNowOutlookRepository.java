package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceInvestmentThenNowOutlook;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceInvestmentThenNowOutlookRepository
        extends JpaRepository<FinanceInvestmentThenNowOutlook, Long> {

    Optional<FinanceInvestmentThenNowOutlook> findByOwnerUserId(long ownerUserId);
}
