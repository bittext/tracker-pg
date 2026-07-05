package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodCryptoOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobinhoodCryptoOrderRepository extends JpaRepository<RobinhoodCryptoOrder, Long> {
    List<RobinhoodCryptoOrder> findTop30ByOwnerUserIdOrderByCreatedAtDesc(long ownerUserId);

    Optional<RobinhoodCryptoOrder> findTopByOwnerUserIdAndSymbolAndSourceOrderByCreatedAtDesc(
            long ownerUserId, String symbol, String source);

    @Query(
            """
            SELECT COUNT(o) FROM RobinhoodCryptoOrder o
            WHERE o.ownerUserId = :uid AND o.source = :source
              AND o.createdAt >= :since
              AND o.status IN ('placed', 'submitted', 'open', 'filled')
            """)
    long countActiveOrdersSince(
            @Param("uid") long ownerUserId, @Param("source") String source, @Param("since") Instant since);

    @Query(
            """
            SELECT COALESCE(SUM(o.estimatedNotional), 0) FROM RobinhoodCryptoOrder o
            WHERE o.ownerUserId = :uid AND o.source = :source
              AND o.createdAt >= :since
              AND o.status IN ('placed', 'submitted', 'open', 'filled')
            """)
    BigDecimal sumEstimatedNotionalSince(
            @Param("uid") long ownerUserId, @Param("source") String source, @Param("since") Instant since);
}
