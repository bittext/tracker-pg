package com.svp.tracker.finance.predicts.dto;

import java.time.Instant;
import java.util.List;

/**
 * Bucket time series for one ticker. The series may contain points from multiple sources when
 * `source=all` was requested; clients should group by {@link PredictsBucketPointDto#source()}.
 */
public record PredictsTimeseriesDto(
        String symbol,
        String bucketSize,
        String source,
        Instant from,
        Instant to,
        List<PredictsBucketPointDto> points) {}
