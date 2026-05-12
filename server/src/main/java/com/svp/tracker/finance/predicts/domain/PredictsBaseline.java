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
@Table(name = "finance_predicts_baselines")
@Getter
@Setter
@NoArgsConstructor
public class PredictsBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "bucket_size", nullable = false, length = 8)
    private String bucketSize;

    @Column(name = "hour_of_week", nullable = false)
    private short hourOfWeek;

    @Column(name = "msg_count_mean", nullable = false, precision = 10, scale = 4)
    private BigDecimal msgCountMean = BigDecimal.ZERO;

    @Column(name = "msg_count_stddev", nullable = false, precision = 10, scale = 4)
    private BigDecimal msgCountStddev = BigDecimal.ZERO;

    @Column(name = "unique_authors_mean", nullable = false, precision = 10, scale = 4)
    private BigDecimal uniqueAuthorsMean = BigDecimal.ZERO;

    @Column(name = "unique_authors_stddev", nullable = false, precision = 10, scale = 4)
    private BigDecimal uniqueAuthorsStddev = BigDecimal.ZERO;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
