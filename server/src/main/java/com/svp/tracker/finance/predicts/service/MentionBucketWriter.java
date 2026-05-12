package com.svp.tracker.finance.predicts.service;

import com.svp.tracker.finance.predicts.domain.PredictsBucket;
import com.svp.tracker.finance.predicts.domain.PredictsBucketSize;
import com.svp.tracker.finance.predicts.domain.PredictsMention;
import com.svp.tracker.finance.predicts.repository.PredictsBucketRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Folds a batch of freshly persisted {@link PredictsMention} rows into the four canonical bucket sizes
 * (5m / 15m / 1h / 1d). Each bucket is upserted: if it exists we increment its counters in place,
 * otherwise we create a new row. {@code unique_authors} is approximated as the count of distinct author
 * hashes <em>within this batch only</em>; full distinct-author counts are recomputed in the nightly
 * baseline job using a single SQL group-by which is more accurate at long ranges.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MentionBucketWriter {

    private final PredictsBucketRepository bucketRepository;

    @Transactional
    public void fold(String symbol, String source, List<PredictsMention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }
        for (PredictsBucketSize size : PredictsBucketSize.values()) {
            foldOneSize(symbol, source, mentions, size);
        }
    }

    private void foldOneSize(String symbol, String source, List<PredictsMention> mentions, PredictsBucketSize size) {
        Map<Instant, List<PredictsMention>> byBucket = new HashMap<>();
        for (PredictsMention m : mentions) {
            Instant start = size.truncate(m.getPostedAt());
            byBucket.computeIfAbsent(start, k -> new java.util.ArrayList<>()).add(m);
        }
        for (Map.Entry<Instant, List<PredictsMention>> entry : byBucket.entrySet()) {
            Instant start = entry.getKey();
            List<PredictsMention> chunk = entry.getValue();
            PredictsBucket bucket = bucketRepository
                    .findBySymbolAndSourceAndBucketSizeAndBucketStart(symbol, source, size.wire(), start)
                    .orElseGet(() -> newBucket(symbol, source, size.wire(), start));
            int pos = 0;
            int neg = 0;
            int neu = 0;
            int engagement = 0;
            BigDecimal sentimentSum = BigDecimal.ZERO;
            Set<String> authors = new HashSet<>();
            for (PredictsMention m : chunk) {
                String label = m.getSentimentLabel() == null ? "" : m.getSentimentLabel().toLowerCase();
                if ("positive".equals(label)) {
                    pos++;
                } else if ("negative".equals(label)) {
                    neg++;
                } else {
                    neu++;
                }
                engagement += Math.max(0, m.getEngagementScore());
                if (m.getSentimentScore() != null) {
                    sentimentSum = sentimentSum.add(m.getSentimentScore());
                }
                if (m.getAuthorHash() != null && !m.getAuthorHash().isBlank()) {
                    authors.add(m.getAuthorHash());
                }
            }
            int newMsgCount = bucket.getMsgCount() + chunk.size();
            int newPos = bucket.getPosCount() + pos;
            int newNeg = bucket.getNegCount() + neg;
            int newNeu = bucket.getNeuCount() + neu;
            int newEngagement = bucket.getEngagementSum() + engagement;
            BigDecimal newSum = bucket.getSentimentSum().add(sentimentSum);
            BigDecimal avg = newMsgCount == 0
                    ? null
                    : newSum.divide(BigDecimal.valueOf(newMsgCount), 4, RoundingMode.HALF_UP);
            bucket.setMsgCount(newMsgCount);
            bucket.setPosCount(newPos);
            bucket.setNegCount(newNeg);
            bucket.setNeuCount(newNeu);
            bucket.setEngagementSum(newEngagement);
            bucket.setSentimentSum(newSum);
            bucket.setSentimentAvg(avg);
            bucket.setUniqueAuthors(Math.max(bucket.getUniqueAuthors(), authors.size()));
            bucket.setUpdatedAt(Instant.now());
            bucketRepository.save(bucket);
        }
    }

    private PredictsBucket newBucket(String symbol, String source, String size, Instant start) {
        PredictsBucket b = new PredictsBucket();
        b.setSymbol(symbol);
        b.setSource(source);
        b.setBucketSize(size);
        b.setBucketStart(start);
        return b;
    }
}
