package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceTaxDeskDailySnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FinanceTaxDeskDailySnapshotRepository extends JpaRepository<FinanceTaxDeskDailySnapshot, Long> {

    Optional<FinanceTaxDeskDailySnapshot> findByOwnerUserIdAndTaxYearAndAsOfDate(
            long ownerUserId, int taxYear, LocalDate asOfDate);

    List<FinanceTaxDeskDailySnapshot> findByOwnerUserIdAndTaxYearOrderByAsOfDateDesc(long ownerUserId, int taxYear);

    @Query("SELECT DISTINCT s.ownerUserId FROM FinanceTaxDeskDailySnapshot s")
    List<Long> findDistinctOwnerUserIds();
}
