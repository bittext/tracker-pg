package com.svp.tracker.finance.predicts.service;

import java.math.BigDecimal;

/**
 * Per-text sentiment result returned by {@link SentimentScorer}. {@code score} is polarity in {@code [-1, +1]}
 * ({@code positive − negative}) so it composes additively into bucket sums; {@code label} is the argmax of the
 * three probabilities and is what we persist on {@code finance_predicts_mentions.sentiment_label}.
 */
public record SentimentScore(String label, BigDecimal score, BigDecimal confidence) {

    public static final SentimentScore NEUTRAL_FALLBACK =
            new SentimentScore("neutral", BigDecimal.ZERO, BigDecimal.ZERO);

    public boolean isPositive() {
        return "positive".equalsIgnoreCase(label);
    }

    public boolean isNegative() {
        return "negative".equalsIgnoreCase(label);
    }
}
