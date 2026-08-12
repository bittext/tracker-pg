package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodTradeInterest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodTradeInterestRepository extends JpaRepository<RobinhoodTradeInterest, Long> {

    Optional<RobinhoodTradeInterest> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<RobinhoodTradeInterest> findByOwnerUserIdOrderByPlannedAtDescIdDesc(long ownerUserId);

    List<RobinhoodTradeInterest> findByOwnerUserIdAndStatusOrderByPlannedAtDescIdDesc(
            long ownerUserId, String status);
}
