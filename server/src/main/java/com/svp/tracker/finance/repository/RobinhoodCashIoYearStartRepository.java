package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodCashIoYearStart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodCashIoYearStartRepository extends JpaRepository<RobinhoodCashIoYearStart, Long> {

    Optional<RobinhoodCashIoYearStart> findByOwnerUserIdAndAccountSuffixAndYear(
            long ownerUserId, String accountSuffix, int year);
}
