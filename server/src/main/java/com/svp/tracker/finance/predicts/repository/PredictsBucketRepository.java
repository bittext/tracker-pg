package com.svp.tracker.finance.predicts.repository;

import com.svp.tracker.finance.predicts.domain.PredictsBucket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictsBucketRepository extends JpaRepository<PredictsBucket, Long> {

    Optional<PredictsBucket> findBySymbolAndSourceAndBucketSizeAndBucketStart(
            String symbol, String source, String bucketSize, Instant bucketStart);

    List<PredictsBucket> findBySymbolAndBucketSizeAndBucketStartAfterOrderByBucketStartAsc(
            String symbol, String bucketSize, Instant after);

    List<PredictsBucket> findBySymbolAndSourceAndBucketSizeAndBucketStartAfterOrderByBucketStartAsc(
            String symbol, String source, String bucketSize, Instant after);
}
