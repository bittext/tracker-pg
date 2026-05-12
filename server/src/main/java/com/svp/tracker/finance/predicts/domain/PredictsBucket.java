package com.svp.tracker.finance.predicts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_predicts_buckets")
@Getter
@Setter
@NoArgsConstructor
public class PredictsBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "bucket_size", nullable = false, length = 8)
    private String bucketSize;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "msg_count", nullable = false)
    private int msgCount;

    @Column(name = "unique_authors", nullable = false)
    private int uniqueAuthors;

    @Column(name = "pos_count", nullable = false)
    private int posCount;

    @Column(name = "neg_count", nullable = false)
    private int negCount;

    @Column(name = "neu_count", nullable = false)
    private int neuCount;

    @Column(name = "engagement_sum", nullable = false)
    private int engagementSum;

    @Column(name = "sentiment_sum", nullable = false, precision = 12, scale = 4)
    private BigDecimal sentimentSum = BigDecimal.ZERO;

    @Column(name = "sentiment_avg", precision = 6, scale = 4)
    private BigDecimal sentimentAvg;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
