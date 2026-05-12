package com.svp.tracker.finance.predicts.service;

import java.util.List;

/**
 * Abstraction over the sentiment engine used by Predicts ingestion. Today the default implementation is
 * {@link FinbertSentimentClient} which calls the Python sidecar and falls back to a built-in heuristic
 * when the sidecar is unreachable. The interface stays small so we can swap in a DJL-based scorer or an
 * external API without touching ingestion.
 */
public interface SentimentScorer {

    /**
     * Score a batch of texts in input order. Implementations may chunk under the hood; callers should treat
     * the response as 1:1 with input. Never returns null; an entry will always at least be neutral.
     */
    List<SentimentScore> score(List<String> texts);
}
