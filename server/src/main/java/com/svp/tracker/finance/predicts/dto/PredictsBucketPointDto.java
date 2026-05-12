package com.svp.tracker.finance.predicts.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One point of a {@code (symbol, source, bucket_size)} time series. Counts are the raw aggregates and
 * {@code sentimentAvg} is the message-weighted polarity in {@code [-1, +1]} ({@code positive − negative}).
 */
public record PredictsBucketPointDto(
        Instant bucketStart,
        String source,
        int msgCount,
        int uniqueAuthors,
        int posCount,
        int negCount,
        int neuCount,
        int engagementSum,
        BigDecimal sentimentAvg) {}
