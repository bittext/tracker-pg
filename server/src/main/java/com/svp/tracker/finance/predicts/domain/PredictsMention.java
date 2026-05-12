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
@Table(name = "finance_predicts_mentions")
@Getter
@Setter
@NoArgsConstructor
public class PredictsMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "source_msg_id", length = 128)
    private String sourceMsgId;

    @Column(name = "text_hash", length = 64, columnDefinition = "char(64)")
    private String textHash;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "body_preview", length = 240)
    private String bodyPreview;

    @Column(name = "author_hash", length = 64, columnDefinition = "char(64)")
    private String authorHash;

    @Column(name = "engagement_score", nullable = false)
    private int engagementScore;

    @Column(name = "native_sentiment", length = 16)
    private String nativeSentiment;

    @Column(name = "sentiment_label", length = 16)
    private String sentimentLabel;

    @Column(name = "sentiment_score", precision = 6, scale = 4)
    private BigDecimal sentimentScore;

    @Column(precision = 6, scale = 4)
    private BigDecimal confidence;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    @Column(length = 512)
    private String url;
}
