package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobinhoodAgenticOrderRepository extends JpaRepository<RobinhoodAgenticOrder, Long> {
    List<RobinhoodAgenticOrder> findByOwnerUserIdOrderByCreatedAtDesc(long ownerUserId);

    List<RobinhoodAgenticOrder> findByOwnerUserIdAndStatusOrderByCreatedAtDesc(long ownerUserId, String status);

    Optional<RobinhoodAgenticOrder> findByIdAndOwnerUserId(long id, long ownerUserId);

    Optional<RobinhoodAgenticOrder> findTopByOwnerUserIdAndSymbolAndSourceOrderByCreatedAtDesc(
            long ownerUserId, String symbol, String source);

    @Query(
            """
            SELECT COUNT(o) FROM RobinhoodAgenticOrder o
            WHERE o.ownerUserId = :uid AND o.source = :source
              AND o.createdAt >= :since
              AND o.status IN ('placed', 'pending_approval')
            """)
    long countActiveOrdersSince(
            @Param("uid") long ownerUserId, @Param("source") String source, @Param("since") Instant since);

    @Query(
            """
            SELECT COALESCE(SUM(o.estimatedNotional), 0) FROM RobinhoodAgenticOrder o
            WHERE o.ownerUserId = :uid AND o.source = :source
              AND o.createdAt >= :since
              AND o.status IN ('placed', 'pending_approval')
            """)
    BigDecimal sumEstimatedNotionalSince(
            @Param("uid") long ownerUserId, @Param("source") String source, @Param("since") Instant since);
}
