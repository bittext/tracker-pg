package com.svp.tracker.finance.tax;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Best-effort fields parsed from Form 1040 PDF text (layout varies by year and software). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Form1040ParsedSummary {

    private boolean likelyForm1040;
    /** Short note, e.g. how many amounts were found. */
    private String parseNote;

    /** Line 1a — wages, salaries, tips (Form W-2 box 1). */
    private BigDecimal wagesSalariesTips;

    private BigDecimal taxableInterest;
    private BigDecimal ordinaryDividends;
    private BigDecimal adjustedGrossIncome;
    private BigDecimal taxableIncome;
    private BigDecimal totalTax;
    /** Line 25d — federal income tax withheld (combined W-2 / 1099 when shown that way). */
    private BigDecimal federalIncomeTaxWithheld;
    private BigDecimal estimatedTaxPayments;
    private BigDecimal refund;
    private BigDecimal amountOwed;
}
