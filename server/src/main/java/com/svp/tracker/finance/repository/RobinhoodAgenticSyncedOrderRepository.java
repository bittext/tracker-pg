package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticSyncedOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobinhoodAgenticSyncedOrderRepository extends JpaRepository<RobinhoodAgenticSyncedOrder, Long> {

    List<RobinhoodAgenticSyncedOrder> findTop10ByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(long ownerUserId);

    List<RobinhoodAgenticSyncedOrder> findByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(long ownerUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RobinhoodAgenticSyncedOrder o where o.ownerUserId = :uid")
    void deleteAllByOwnerUserId(@Param("uid") long ownerUserId);
}
