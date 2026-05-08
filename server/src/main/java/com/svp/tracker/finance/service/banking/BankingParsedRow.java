package com.svp.tracker.finance.service.banking;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param ofxFitId {@code <FITID>} from OFX/QFX when present; enables stable dedupe for Plaid and other OFX exports
 *     across re-imports (description can change when pending clears).
 */
public record BankingParsedRow(LocalDate date, BigDecimal amount, String description, String ofxFitId) {

    public BankingParsedRow(LocalDate date, BigDecimal amount, String description) {
        this(date, amount, description, null);
    }
}
