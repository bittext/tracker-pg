package com.svp.tracker.finance.tax;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Best-effort fields parsed from Form 1040 PDF text (layout varies by year and software). Display is refreshed from
 * stored extract when possible so parser improvements apply without re-uploading.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Form1040ParsedSummary {

    private boolean likelyForm1040;
    /** Short note, e.g. how many amounts were found. */
    private String parseNote;

    /** Year printed on the return (from PDF text), if detected. */
    private String taxYearOnForm;
    /** Filing status checkboxes / phrases, if detected. */
    private String filingStatus;

    /** Line 1a — wages, salaries, tips (Form W-2 box 1). */
    private BigDecimal wagesSalariesTips;
    private BigDecimal taxableInterest;
    private BigDecimal ordinaryDividends;
    /** Line 4b — IRA distributions (taxable). */
    private BigDecimal iraDistributionsTaxable;
    /** Line 5b — Pensions and annuities (taxable). */
    private BigDecimal pensionsTaxable;
    /** Line 6b — Social security benefits (taxable). */
    private BigDecimal socialSecurityTaxable;
    /** Line 9 — total income (before adjustments). */
    private BigDecimal totalIncome;
    private BigDecimal adjustedGrossIncome;
    /** Line 12 — standard deduction or (often) itemized / deduction amount column. */
    private BigDecimal standardOrItemizedDeduction;
    private BigDecimal taxableIncome;
    private BigDecimal totalTax;
    /** Line 19 — child tax credit / credit for other dependents (combined on some extracts). */
    private BigDecimal childAndOtherDependentsCredit;
    /** Line 22 or 24 — total tax after credits (wording varies). */
    private BigDecimal totalTaxAfterCredits;
    /** Line 25d — federal income tax withheld. */
    private BigDecimal federalIncomeTaxWithheld;
    private BigDecimal estimatedTaxPayments;
    /** Line 33 — total payments, credits, and withholding. */
    private BigDecimal totalPayments;
    private BigDecimal refund;
    private BigDecimal amountOwed;
}
