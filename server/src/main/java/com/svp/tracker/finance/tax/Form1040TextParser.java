package com.svp.tracker.finance.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic parser for text extracted from IRS Form 1040 PDFs. Filled forms differ by tax year and
 * preparer software; this never replaces reading the official PDF.
 */
public final class Form1040TextParser {

    private static final Pattern MONEY =
            Pattern.compile("(?:\\(\\s*)?\\$?\\s*([\\d,]+\\.\\d{2})\\s*\\)?");

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

        BigDecimal agi = firstMoneyNear(text, "ADJUSTED GROSS INCOME", "11 ");
        if (agi == null) {
            agi = firstMoneyNear(text, "LINE 11", "ADJUSTED GROSS");
        }

        Form1040ParsedSummary s = Form1040ParsedSummary.builder()
                .likelyForm1040(likely)
                .wagesSalariesTips(firstMoneyNear(text, "1a", "TOTAL AMOUNT FROM FORM", "W-2", "BOX 1"))
                .taxableInterest(firstMoneyNear(text, "2b", "TAXABLE INTEREST"))
                .ordinaryDividends(firstMoneyNear(text, "3b", "ORDINARY DIVIDENDS"))
                .adjustedGrossIncome(agi)
                .taxableIncome(firstMoneyNear(text, "TAXABLE INCOME", "15 "))
                .totalTax(firstMoneyNear(text, "TOTAL TAX", "16 "))
                .federalIncomeTaxWithheld(firstMoneyNear(
                        text,
                        "FEDERAL INCOME TAX WITHHELD",
                        "25d",
                        "FROM FORM(S) W-2",
                        "WITHHOLDING"))
                .estimatedTaxPayments(firstMoneyNear(text, "ESTIMATED TAX PAYMENTS", "26 "))
                .refund(firstMoneyNear(text, "REFUND", "35a", "34 "))
                .amountOwed(firstMoneyNear(text, "AMOUNT YOU OWE", "37 ", "AMOUNT OWED"))
                .build();

        int hits = countNonNullMoney(s);
        String note = likely
                ? "Parsed " + hits + " numeric field(s) from PDF text (best effort)."
                : "This file may not be a Form 1040; values are best-effort only.";
        s.setParseNote(note);
        return s;
    }

    private static int countNonNullMoney(Form1040ParsedSummary s) {
        int n = 0;
        if (s.getWagesSalariesTips() != null) n++;
        if (s.getTaxableInterest() != null) n++;
        if (s.getOrdinaryDividends() != null) n++;
        if (s.getAdjustedGrossIncome() != null) n++;
        if (s.getTaxableIncome() != null) n++;
        if (s.getTotalTax() != null) n++;
        if (s.getFederalIncomeTaxWithheld() != null) n++;
        if (s.getEstimatedTaxPayments() != null) n++;
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
