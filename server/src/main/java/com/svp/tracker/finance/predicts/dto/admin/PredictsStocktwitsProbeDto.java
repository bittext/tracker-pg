package com.svp.tracker.finance.predicts.dto.admin;

/**
 * Result of an admin-triggered direct call against the StockTwits public symbol stream endpoint.
 * Returned by {@code GET /api/admin/finance/predicts/diag/stocktwits?symbol=...}. Captures enough to
 * answer the common operational question: "is StockTwits returning 404s because the symbol is
 * unindexed, or because our egress IP is being blocked?"
 *
 * @param symbol normalised symbol that was queried
 * @param url full URL hit (after any base-url normalisation)
 * @param userAgent exact User-Agent header that was sent
 * @param status HTTP status code observed (0 if the request never completed)
 * @param elapsedMs wall-clock duration of the request
 * @param bodyPreview first ~400 chars of the response body (or error stack for transport failures)
 * @param messageCount number of {@code messages[]} entries when the response parses as a valid stream,
 *     {@code null} otherwise — a 200 with {@code messageCount > 0} confirms full happy path
 * @param transportError {@code true} for connect/IO failures (the upstream wasn't reachable at all)
 * @param errorMessage short summary suitable for surfacing to admin UI
 */
public record PredictsStocktwitsProbeDto(
        String symbol,
        String url,
        String userAgent,
        int status,
        long elapsedMs,
        String bodyPreview,
        Integer messageCount,
        boolean transportError,
        String errorMessage) {}
