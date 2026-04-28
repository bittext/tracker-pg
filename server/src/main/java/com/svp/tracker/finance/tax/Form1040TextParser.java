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

    private static final String PARSER_VERSION = "1040-parser-v2";
    private static final Pattern MONEY =
            Pattern.compile("(?:\\(\\s*)?\\$?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)\\.?\\s*\\)?");
    private static final Pattern YEAR_WORD = Pattern.compile("\\b(20\\d{2})\\b");

    private Form1040TextParser() {}

    public static Form1040ParsedSummary parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Form1040ParsedSummary.builder()
                    .likelyForm1040(false)
                    .parserVersion(PARSER_VERSION)
                    .confidenceLabel("LOW")
                    .parsedAmountFieldCount(0)
                    .parseNote("No extractable text in PDF.")
                    .build();
        }
        String text = raw.replace('\r', '\n');
        String upper = text.toUpperCase(Locale.ROOT);
        String[] lines = text.split("\\n");
        boolean likely = upper.contains("FORM 1040")
                || upper.contains("U.S. INDIVIDUAL INCOME TAX RETURN")
                || (upper.contains("1040") && upper.contains("DEPARTMENT OF THE TREASURY"));

        BigDecimal line1a = amountForLine(lines, "1A", "WAGES", "SALARIES", "TIPS");
        BigDecimal line24 = amountForLine(lines, "24", "TOTAL TAX");
        BigDecimal line37 = amountForLine(lines, "37", "AMOUNT YOU OWE", "AMOUNT OWED", "BALANCE DUE");

        Form1040ParsedSummary s = Form1040ParsedSummary.builder()
                .likelyForm1040(likely)
                .parserVersion(PARSER_VERSION)
                .taxYearOnForm(detectTaxYearOnForm(text))
                .filingStatus(detectFilingStatus(upper))
                .wagesSalariesTips(line1a != null ? line1a : bestAmount(lines, text, "1a", "wages", "salaries", "tips"))
                .taxableInterest(bestAmount(lines, text, "2b", "taxable interest"))
                .ordinaryDividends(bestAmount(lines, text, "3b", "ordinary dividends"))
                .iraDistributionsTaxable(bestAmount(lines, text, "4b", "ira distributions"))
                .pensionsTaxable(bestAmount(lines, text, "5b", "pensions and annuities"))
                .socialSecurityTaxable(bestAmount(lines, text, "6b", "social security benefits"))
                .totalIncome(bestAmount(lines, text, "line 9", "total income"))
                .adjustedGrossIncome(bestAmount(lines, text, "line 11", "adjusted gross income"))
                .standardOrItemizedDeduction(bestAmount(lines, text, "line 12", "standard deduction", "itemized deduction"))
                .taxableIncome(bestAmount(lines, text, "line 15", "taxable income", "15"))
                .totalTax(line24 != null ? line24 : bestAmount(lines, text, "line 16", "total tax", "16"))
                .childAndOtherDependentsCredit(bestAmount(lines, text, "line 19", "child tax credit", "credit for other dependents"))
                .totalTaxAfterCredits(line24 != null ? line24 : bestAmount(lines, text, "line 24", "24", "line 22", "after credits"))
                .federalIncomeTaxWithheld(bestAmount(lines, text, "25d", "federal income tax withheld"))
                .estimatedTaxPayments(bestAmount(lines, text, "line 26", "estimated tax payments"))
                .totalPayments(bestAmount(lines, text, "line 33", "total payments"))
                .refund(bestAmount(lines, text, "line 35", "35a", "refund", "overpayment"))
                .amountOwed(line37 != null ? line37 : bestAmount(lines, text, "line 37", "37", "amount you owe", "amount owed", "balance due"))
                .build();

        int hits = countNonNullMoney(s);
        List<String> warnings = buildWarnings(s, likely, hits);
        String confidence = confidenceLabel(likely, hits);
        String note = likely
                ? "Parsed " + hits
                        + " important field(s) from the uploaded return using line-aware matching. Verify against the PDF."
                : "This file may not be a Form 1040; values are best-effort only.";
        s.setConfidenceLabel(confidence);
        s.setParsedAmountFieldCount(hits);
        s.setParseWarnings(warnings.isEmpty() ? null : warnings);
        s.setParseNote(note);
        return s;
    }

    private static List<String> buildWarnings(Form1040ParsedSummary s, boolean likely, int hits) {
        List<String> warnings = new ArrayList<>();
        if (!likely) {
            warnings.add("Document markers do not strongly match Form 1040.");
        }
        if (hits < 5) {
            warnings.add("Only a few amount fields were detected. PDF may be scanned image or unusual layout.");
        }
        if (s.getTaxYearOnForm() == null) {
            warnings.add("Could not confidently detect the tax year printed on the form.");
        }
        if (s.getFilingStatus() == null) {
            warnings.add("Could not confidently detect filing status.");
        }
        if (s.getRefund() == null && s.getAmountOwed() == null) {
            warnings.add("Could not identify refund or amount owed line.");
        }
        return warnings;
    }

    private static String confidenceLabel(boolean likely, int hits) {
        if (!likely) {
            return "LOW";
        }
        if (hits >= 10) {
            return "HIGH";
        }
        if (hits >= 6) {
            return "MEDIUM";
        }
        return "LOW";
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

    private static BigDecimal bestAmount(String[] lines, String fullText, String... keywords) {
        BigDecimal fromLines = amountNearLine(lines, keywords);
        if (fromLines != null) {
            return fromLines;
        }
        return firstMoneyNear(fullText, keywords);
    }

    /**
     * Targeted line parser for Form 1040 lines (e.g., 1a, 24, 37).
     * Prefers right-most amount on the matching line, then nearby continuation lines.
     */
    private static BigDecimal amountForLine(String[] lines, String lineToken, String... keywords) {
        if (lines == null || lines.length == 0) {
            return null;
        }
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i];
            String up = line.toUpperCase(Locale.ROOT);
            if (!containsLineToken(up, lineToken)) {
                continue;
            }
            if (!containsAny(up, keywords)) {
                continue;
            }
            List<BigDecimal> same = moneyTokens(line);
            if (!same.isEmpty()) {
                return same.get(same.size() - 1).setScale(2, RoundingMode.HALF_UP);
            }
            for (int j = i + 1; j <= Math.min(lines.length - 1, i + 2); j++) {
                List<BigDecimal> next = moneyTokens(lines[j]);
                if (!next.isEmpty()) {
                    return next.get(next.size() - 1).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        return null;
    }

    private static boolean containsAny(String upper, String... keywords) {
        if (keywords == null || keywords.length == 0) {
            return true;
        }
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && upper.contains(kw.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLineToken(String upper, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        String compact = upper.replaceAll("\\s+", "");
        if (compact.contains("LINE" + t)) {
            return true;
        }
        if ("1A".equals(t) && (compact.contains("1A") || compact.contains("1.A"))) {
            return true;
        }
        return compact.contains(t);
    }

    private static BigDecimal amountNearLine(String[] lines, String... keywords) {
        if (lines == null || lines.length == 0) {
            return null;
        }
        int bestIdx = -1;
        int bestScore = 0;
        for (int i = 0; i < lines.length; i++) {
            String up = lines[i] == null ? "" : lines[i].toUpperCase(Locale.ROOT);
            int score = 0;
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank() && up.contains(kw.toUpperCase(Locale.ROOT))) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        if (bestIdx < 0 || bestScore == 0) {
            return null;
        }
        List<BigDecimal> candidates = new ArrayList<>();
        for (int i = bestIdx; i <= Math.min(lines.length - 1, bestIdx + 3); i++) {
            candidates.addAll(moneyTokens(lines[i]));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(candidates.size() - 1).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<BigDecimal> moneyTokens(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        List<BigDecimal> out = new ArrayList<>();
        Matcher m = MONEY.matcher(line);
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
            out.add(amt);
        }
        return out;
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
