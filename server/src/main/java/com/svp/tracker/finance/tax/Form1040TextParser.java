package com.svp.tracker.finance.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic parser for text extracted from IRS Form 1040 PDFs. Filled forms differ by tax year and preparer software;
 * this never replaces reading the official PDF.
 */
public final class Form1040TextParser {

    private static final Pattern MONEY =
            Pattern.compile("(?:\\(\\s*)?\\$?\\s*([\\d,]+\\.\\d{2})\\s*\\)?");
    private static final Pattern YEAR_WORD = Pattern.compile("\\b(20\\d{2})\\b");

    private Form1040TextParser() {}

    public static Form1040ParsedSummary parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Form1040ParsedSummary.builder()
                    .likelyForm1040(false)
                    .parseNote("No extractable text in PDF.")
                    .build();
        }
        String text = raw.replace('\r', '\n');
        String upper = text.toUpperCase(Locale.ROOT);
        boolean likely = upper.contains("FORM 1040")
                || upper.contains("U.S. INDIVIDUAL INCOME TAX RETURN")
                || (upper.contains("1040") && upper.contains("DEPARTMENT OF THE TREASURY"));

        BigDecimal agi = firstMoneyNear(text, "ADJUSTED GROSS INCOME", "LINE 11", "11 ");
        if (agi == null) {
            agi = firstMoneyNear(text, "ADJUSTED GROSS");
        }

        BigDecimal refund = firstMoneyNear(text, "REFUND", "LINE 35", "35a", "OVERPAYMENT", "34 ");
        if (refund == null) {
            refund = firstMoneyNear(text, "DIRECT DEPOSIT", "ROUTING NUMBER");
        }
        BigDecimal owed = firstMoneyNear(text, "AMOUNT YOU OWE", "LINE 37", "37 ", "AMOUNT OWED", "BALANCE DUE");

        Form1040ParsedSummary s = Form1040ParsedSummary.builder()
                .likelyForm1040(likely)
                .taxYearOnForm(detectTaxYearOnForm(text))
                .filingStatus(detectFilingStatus(upper))
                .wagesSalariesTips(firstMoneyNear(text, "1a", "TOTAL AMOUNT FROM FORM", "W-2", "BOX 1", "WAGES, SALARIES"))
                .taxableInterest(firstMoneyNear(text, "2b", "TAXABLE INTEREST"))
                .ordinaryDividends(firstMoneyNear(text, "3b", "ORDINARY DIVIDENDS"))
                .iraDistributionsTaxable(firstMoneyNear(text, "4b", "IRA DISTRIBUTIONS", "IRA DISTRIBUTION"))
                .pensionsTaxable(firstMoneyNear(text, "5b", "PENSIONS AND ANNUITIES", "PENSION AND ANNUITY"))
                .socialSecurityTaxable(firstMoneyNear(text, "6b", "SOCIAL SECURITY BENEFITS", "TAXABLE AMOUNT"))
                .totalIncome(firstMoneyNear(text, "TOTAL INCOME", "9 ", "LINE 9"))
                .adjustedGrossIncome(agi)
                .standardOrItemizedDeduction(firstMoneyNear(
                        text, "STANDARD DEDUCTION", "ITEMIZED DEDUCTIONS", "12 ", "LINE 12", "DEDUCTION FROM"))
                .taxableIncome(firstMoneyNear(text, "TAXABLE INCOME", "15 ", "LINE 15"))
                .totalTax(firstMoneyNear(text, "TOTAL TAX", "16 ", "LINE 16"))
                .childAndOtherDependentsCredit(firstMoneyNear(
                        text,
                        "CHILD TAX CREDIT",
                        "CREDIT FOR OTHER DEPENDENTS",
                        "19 ",
                        "LINE 19",
                        "8812"))
                .totalTaxAfterCredits(firstMoneyNear(
                        text,
                        "TOTAL TAX AFTER CREDITS",
                        "AFTER CREDITS",
                        "22 ",
                        "24 ",
                        "LINE 22",
                        "LINE 24"))
                .federalIncomeTaxWithheld(firstMoneyNear(
                        text,
                        "FEDERAL INCOME TAX WITHHELD",
                        "25d",
                        "FROM FORM(S) W-2",
                        "WITHHOLDING",
                        "FEDERAL INCOME TAX WITHHELD"))
                .estimatedTaxPayments(firstMoneyNear(text, "ESTIMATED TAX PAYMENTS", "26 ", "LINE 26"))
                .totalPayments(firstMoneyNear(text, "TOTAL PAYMENTS", "33 ", "LINE 33", "TOTAL PAYMENT"))
                .refund(refund)
                .amountOwed(owed)
                .build();

        int hits = countNonNullMoney(s);
        String note = likely
                ? "Parsed " + hits + " important field(s) from PDF text (best effort; verify on your official return)."
                : "This file may not be a Form 1040; values are best-effort only.";
        s.setParseNote(note);
        return s;
    }

    private static String detectTaxYearOnForm(String text) {
        int head = Math.min(text.length(), 4000);
        Matcher m = YEAR_WORD.matcher(text.substring(0, head));
        while (m.find()) {
            try {
                int y = Integer.parseInt(m.group(1));
                if (y >= 2015 && y <= 2032) {
                    return m.group(1);
                }
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return null;
    }

    private static String detectFilingStatus(String upper) {
        if (upper.contains("QUALIFYING SURVIVING SPOUSE")) {
            return "Qualifying surviving spouse";
        }
        if (upper.contains("HEAD OF HOUSEHOLD")) {
            return "Head of household";
        }
        if (upper.contains("MARRIED FILING SEPARATELY")) {
            return "Married filing separately";
        }
        if (upper.contains("MARRIED FILING JOINTLY")) {
            return "Married filing jointly";
        }
        if (upper.contains("MARRIED FILING")) {
            return "Married filing (unspecified)";
        }
        if (upper.contains("SINGLE") && upper.contains("FILING STATUS")) {
            return "Single";
        }
        if (upper.contains("\nSINGLE") || upper.contains(" SINGLE ")) {
            return "Single";
        }
        return null;
    }

    private static int countNonNullMoney(Form1040ParsedSummary s) {
        int n = 0;
        if (s.getWagesSalariesTips() != null) n++;
        if (s.getTaxableInterest() != null) n++;
        if (s.getOrdinaryDividends() != null) n++;
        if (s.getIraDistributionsTaxable() != null) n++;
        if (s.getPensionsTaxable() != null) n++;
        if (s.getSocialSecurityTaxable() != null) n++;
        if (s.getTotalIncome() != null) n++;
        if (s.getAdjustedGrossIncome() != null) n++;
        if (s.getStandardOrItemizedDeduction() != null) n++;
        if (s.getTaxableIncome() != null) n++;
        if (s.getTotalTax() != null) n++;
        if (s.getChildAndOtherDependentsCredit() != null) n++;
        if (s.getTotalTaxAfterCredits() != null) n++;
        if (s.getFederalIncomeTaxWithheld() != null) n++;
        if (s.getEstimatedTaxPayments() != null) n++;
        if (s.getTotalPayments() != null) n++;
        if (s.getRefund() != null) n++;
        if (s.getAmountOwed() != null) n++;
        return n;
    }

    private static BigDecimal firstMoneyNear(String text, String... keywords) {
        for (String kw : keywords) {
            int idx = indexOfIgnoreCase(text, kw);
            if (idx < 0) {
                continue;
            }
            int end = Math.min(text.length(), idx + 900);
            BigDecimal found = lastMoneyInWindow(text, idx, end);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toUpperCase(Locale.ROOT).indexOf(needle.toUpperCase(Locale.ROOT));
    }

    private static BigDecimal lastMoneyInWindow(String text, int start, int end) {
        String slice = text.substring(start, end);
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher m = MONEY.matcher(slice);
        while (m.find()) {
            String g = m.group(1);
            boolean parens = m.group(0).trim().startsWith("(");
            BigDecimal amt = parseMoney(g);
            if (amt == null) {
                continue;
            }
            if (parens) {
                amt = amt.negate();
            }
            amounts.add(amt);
        }
        if (amounts.isEmpty()) {
            return null;
        }
        return amounts.get(amounts.size() - 1).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal parseMoney(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replace(",", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
