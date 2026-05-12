package com.svp.tracker.finance.predicts.repository;

import com.svp.tracker.finance.predicts.domain.PredictsMention;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictsMentionRepository extends JpaRepository<PredictsMention, Long> {

    /** Recent mentions for one ticker (any source). */
    List<PredictsMention> findTop50BySymbolOrderByPostedAtDesc(String symbol);

    /** Whether the source already saw this message id (called once per scraped message). */
    boolean existsBySourceAndSourceMsgId(String source, String sourceMsgId);

    /** Drops raw mentions older than the configured retention window. */
    @Modifying
    @Query("DELETE FROM PredictsMention m WHERE m.fetchedAt < :cutoff")
    int deleteByFetchedAtBefore(@Param("cutoff") Instant cutoff);
}
