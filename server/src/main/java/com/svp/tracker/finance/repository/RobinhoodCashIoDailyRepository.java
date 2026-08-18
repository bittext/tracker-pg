package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodCashIoDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodCashIoDailyRepository extends JpaRepository<RobinhoodCashIoDaily, Long> {

    Optional<RobinhoodCashIoDaily> findByOwnerUserIdAndAccountSuffixAndAsOfDate(
            long ownerUserId, String accountSuffix, LocalDate asOfDate);

    List<RobinhoodCashIoDaily> findByOwnerUserIdAndAccountSuffixAndAsOfDateBetweenOrderByAsOfDateDesc(
            long ownerUserId, String accountSuffix, LocalDate from, LocalDate to);

    long countByOwnerUserIdAndAccountSuffixAndAsOfDateBetween(
            long ownerUserId, String accountSuffix, LocalDate from, LocalDate to);
}
