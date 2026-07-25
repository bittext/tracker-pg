package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceInvestmentThenNow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceInvestmentThenNowRepository extends JpaRepository<FinanceInvestmentThenNow, Long> {

    List<FinanceInvestmentThenNow> findByOwnerUserIdOrderByUpdatedAtDesc(long ownerUserId);

    Optional<FinanceInvestmentThenNow> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<FinanceInvestmentThenNow> findByOwnerUserIdAndIdIn(long ownerUserId, List<Long> ids);

    Optional<FinanceInvestmentThenNow> findByOwnerUserIdAndSymbolAndAsOfDateAndInvestedAmount(
            long ownerUserId, String symbol, LocalDate asOfDate, BigDecimal investedAmount);
}
