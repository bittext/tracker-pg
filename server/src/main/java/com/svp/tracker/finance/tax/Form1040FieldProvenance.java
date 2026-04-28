package com.svp.tracker.finance.tax;

import java.util.List;

/**
 * Describes how a field was extracted so users can judge reliability.
 */
public record Form1040FieldProvenance(
        String sourcePass,
        List<String> matchedTokens,
        String confidence,
        String note) {}

