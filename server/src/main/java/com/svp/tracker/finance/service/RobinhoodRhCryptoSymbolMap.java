package com.svp.tracker.finance.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Maps Predicts asset symbols to Robinhood crypto trading pairs. */
public final class RobinhoodRhCryptoSymbolMap {

    private static final Map<String, String> ASSET_TO_PAIR = Map.ofEntries(
            Map.entry("BTC", "BTC-USD"),
            Map.entry("ETH", "ETH-USD"),
            Map.entry("SOL", "SOL-USD"),
            Map.entry("DOGE", "DOGE-USD"),
            Map.entry("ADA", "ADA-USD"),
            Map.entry("XRP", "XRP-USD"),
            Map.entry("LTC", "LTC-USD"),
            Map.entry("BCH", "BCH-USD"),
            Map.entry("ETC", "ETC-USD"),
            Map.entry("LINK", "LINK-USD"),
            Map.entry("AVAX", "AVAX-USD"),
            Map.entry("SHIB", "SHIB-USD"),
            Map.entry("USDC", "USDC-USD"));

    private RobinhoodRhCryptoSymbolMap() {}

    public static Set<String> supportedAssets() {
        return ASSET_TO_PAIR.keySet();
    }

    public static Optional<String> toTradingPair(String assetOrPair) {
        if (assetOrPair == null || assetOrPair.isBlank()) {
            return Optional.empty();
        }
        String normalized = assetOrPair.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("-USD")) {
            String asset = normalized.substring(0, normalized.length() - 4);
            return ASSET_TO_PAIR.containsKey(asset) ? Optional.of(normalized) : Optional.empty();
        }
        return Optional.ofNullable(ASSET_TO_PAIR.get(normalized));
    }

    public static String toAssetCode(String assetOrPair) {
        String normalized = assetOrPair.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("-USD")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    public static Map<String, String> copyPairs() {
        return new LinkedHashMap<>(ASSET_TO_PAIR);
    }
}
