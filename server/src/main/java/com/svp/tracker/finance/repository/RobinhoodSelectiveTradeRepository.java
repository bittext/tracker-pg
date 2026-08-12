package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodSelectiveTrade;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodSelectiveTradeRepository extends JpaRepository<RobinhoodSelectiveTrade, Long> {

    Optional<RobinhoodSelectiveTrade> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<RobinhoodSelectiveTrade> findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
            long ownerUserId, LocalDate fromInclusive, LocalDate toInclusive);
}
