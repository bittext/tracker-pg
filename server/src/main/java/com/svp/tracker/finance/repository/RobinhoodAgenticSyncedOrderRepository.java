package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticSyncedOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticSyncedOrderRepository extends JpaRepository<RobinhoodAgenticSyncedOrder, Long> {

    List<RobinhoodAgenticSyncedOrder> findTop10ByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(long ownerUserId);

    void deleteAllByOwnerUserId(long ownerUserId);
}
