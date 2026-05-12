package com.svp.tracker.finance.predicts.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Cheap fallback that never makes network calls. Used when the FinBERT sidecar is disabled or down so
 * the ingestion pipeline keeps producing usable sentiment values. The lexicon focuses on community
 * trader jargon (cashtag-adjacent, emojis), which performs well on StockTwits and reasonably on Reddit.
 *
 * <p>Confidence is the absolute magnitude of the (pos − neg) word delta divided by the token count, so
 * a single strong word can never push confidence above 1.0.
 */
@Component
public class HeuristicSentimentScorer implements SentimentScorer {

    private static final Set<String> POSITIVE = Set.of(
            "buy",
            "buying",
            "bought",
            "long",
            "calls",
            "bull",
            "bullish",
            "moon",
            "mooning",
            "rocket",
            "rip",
            "ripping",
            "breakout",
            "rally",
            "rallying",
            "beat",
            "beats",
            "outperform",
            "upgrade",
            "upgrades",
            "buy-the-dip",
            "btd",
            "btfd",
            "ath",
            "🚀",
            "📈",
            "💎",
            "🔥",
            "✅",
            "🟢");

    private static final Set<String> NEGATIVE = Set.of(
            "sell",
            "selling",
            "sold",
            "short",
            "shorting",
            "puts",
            "bear",
            "bearish",
            "dump",
            "dumping",
            "crash",
            "crashing",
            "tank",
            "tanking",
            "drop",
            "dropping",
            "miss",
            "missed",
            "downgrade",
            "downgrades",
            "bag",
            "bagholder",
            "rugpull",
            "rug",
            "📉",
            "🔻",
            "💀",
            "🩸",
            "🟥");

    @Override
    public List<SentimentScore> score(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<SentimentScore> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(scoreOne(text));
        }
        return out;
    }

    public SentimentScore scoreOne(String text) {
        if (text == null || text.isBlank()) {
            return SentimentScore.NEUTRAL_FALLBACK;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String[] tokens = lower.split("[\\s\\p{Punct}]+");
        int pos = 0;
        int neg = 0;
        int total = 0;
        for (String t : tokens) {
            if (t.isEmpty()) {
                continue;
            }
            total++;
            if (POSITIVE.contains(t)) {
                pos++;
            } else if (NEGATIVE.contains(t)) {
                neg++;
            }
        }
        // Emoji tokens often arrive without whitespace; sweep substrings too.
        for (String token : POSITIVE) {
            if (token.length() > 1 && lower.contains(token)) {
                pos++;
            }
        }
        for (String token : NEGATIVE) {
            if (token.length() > 1 && lower.contains(token)) {
                neg++;
            }
        }
        int delta = pos - neg;
        if (total == 0) {
            return SentimentScore.NEUTRAL_FALLBACK;
        }
        BigDecimal raw = BigDecimal.valueOf(delta).divide(BigDecimal.valueOf(Math.max(total, 4)), 4, RoundingMode.HALF_UP);
        if (raw.compareTo(new BigDecimal("1.0")) > 0) {
            raw = BigDecimal.ONE;
        } else if (raw.compareTo(new BigDecimal("-1.0")) < 0) {
            raw = BigDecimal.ONE.negate();
        }
        BigDecimal confidence = raw.abs();
        String label;
        if (raw.signum() > 0) {
            label = "positive";
        } else if (raw.signum() < 0) {
            label = "negative";
        } else {
            label = "neutral";
        }
        return new SentimentScore(label, raw, confidence);
    }
}
