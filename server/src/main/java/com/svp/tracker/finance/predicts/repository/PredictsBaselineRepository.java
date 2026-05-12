package com.svp.tracker.finance.predicts.repository;

import com.svp.tracker.finance.predicts.domain.PredictsBaseline;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictsBaselineRepository extends JpaRepository<PredictsBaseline, Long> {

    Optional<PredictsBaseline> findBySymbolAndSourceAndBucketSizeAndHourOfWeek(
            String symbol, String source, String bucketSize, short hourOfWeek);

    List<PredictsBaseline> findBySymbolAndBucketSize(String symbol, String bucketSize);
}
