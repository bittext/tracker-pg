package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticOrderRepository extends JpaRepository<RobinhoodAgenticOrder, Long> {
    List<RobinhoodAgenticOrder> findByOwnerUserIdOrderByCreatedAtDesc(long ownerUserId);

    List<RobinhoodAgenticOrder> findByOwnerUserIdAndStatusOrderByCreatedAtDesc(long ownerUserId, String status);

    Optional<RobinhoodAgenticOrder> findByIdAndOwnerUserId(long id, long ownerUserId);
}
