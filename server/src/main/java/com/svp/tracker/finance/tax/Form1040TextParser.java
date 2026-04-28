package com.svp.tracker.finance.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic key-line parser for Form 1040 extracted text.
 */
public final class Form1040TextParser {

    private static final String PARSER_VERSION = "1040-parser-v3";
    private static final Pattern MONEY =
            Pattern.compile("(?:\\(\\s*)?\\$?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)\\.?\\s*\\)?");
    private static final Pattern YEAR_WORD = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern LINE_1A_TOKEN = Pattern.compile("(?<!\\d)1\\s*[\\.-]?\\s*A(?![A-Z0-9])");
    private static final Pattern LINE_24_TOKEN = Pattern.compile("(?<!\\d)24(?![A-Z0-9])");
    private static final Pattern LINE_37_TOKEN = Pattern.compile("(?<!\\d)37(?![A-Z0-9])");
    private static final Pattern GENERIC_LINE_LABEL = Pattern.compile("^\\s*LINE\\s*([0-9]{1,2}[A-Z]?)\\b");

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

        Map<String, Form1040FieldProvenance> prov = new LinkedHashMap<>();

        FieldResult line1a = parseField(lines, text, "1A", List.of("WAGES", "SALARIES", "TIPS"), List.of("1a", "wages", "salaries", "tips"));
        FieldResult line24 = parseField(lines, text, "24", List.of("TOTAL TAX"), List.of("line 24", "24", "total tax", "line 22", "after credits"));
        FieldResult line37 = parseField(lines, text, "37", List.of("AMOUNT YOU OWE", "AMOUNT OWED", "BALANCE DUE"), List.of("line 37", "37", "amount you owe", "amount owed", "balance due"));

        putProvenance(prov, "wagesSalariesTips", line1a, "Line 1a");
        putProvenance(prov, "totalTaxAfterCredits", line24, "Line 24 (or fallback) total tax after credits");
        putProvenance(prov, "amountOwed", line37, "Line 37 amount owed");

        Form1040ParsedSummary s = Form1040ParsedSummary.builder()
                .likelyForm1040(likely)
                .parserVersion(PARSER_VERSION)
                .fieldProvenance(prov)
                .taxYearOnForm(detectTaxYearOnForm(text))
                .filingStatus(detectFilingStatus(upper))
                .wagesSalariesTips(line1a.amount())
                .taxableInterest(bestAmount(lines, text, List.of("2b", "taxable interest")))
                .ordinaryDividends(bestAmount(lines, text, List.of("3b", "ordinary dividends")))
                .iraDistributionsTaxable(bestAmount(lines, text, List.of("4b", "ira distributions")))
                .pensionsTaxable(bestAmount(lines, text, List.of("5b", "pensions and annuities")))
                .socialSecurityTaxable(bestAmount(lines, text, List.of("6b", "social security benefits")))
                .totalIncome(bestAmount(lines, text, List.of("line 9", "total income")))
                .adjustedGrossIncome(bestAmount(lines, text, List.of("line 11", "adjusted gross income")))
                .standardOrItemizedDeduction(bestAmount(lines, text, List.of("line 12", "standard deduction", "itemized deduction")))
                .taxableIncome(bestAmount(lines, text, List.of("line 15", "taxable income")))
                .totalTax(line24.amount() != null ? line24.amount() : bestAmount(lines, text, List.of("line 16", "total tax")))
                .childAndOtherDependentsCredit(bestAmount(lines, text, List.of("line 19", "child tax credit", "credit for other dependents")))
                .totalTaxAfterCredits(line24.amount())
                .federalIncomeTaxWithheld(bestAmount(lines, text, List.of("25d", "federal income tax withheld")))
                .estimatedTaxPayments(bestAmount(lines, text, List.of("line 26", "estimated tax payments")))
                .totalPayments(bestAmount(lines, text, List.of("line 33", "total payments")))
                .refund(bestAmount(lines, text, List.of("line 35", "35a", "refund", "overpayment")))
                .amountOwed(line37.amount())
                .build();

        int hits = countNonNullMoney(s);
        List<String> warnings = buildWarnings(s, likely, hits);
        appendKeyLineWarnings(warnings, line1a, "Line 1a");
        appendKeyLineWarnings(warnings, line24, "Line 24");
        appendKeyLineWarnings(warnings, line37, "Line 37");
        String confidence = confidenceLabel(likely, hits);
        s.setConfidenceLabel(confidence);
        s.setParsedAmountFieldCount(hits);
        s.setParseWarnings(warnings.isEmpty() ? null : warnings);
        s.setParseNote(likely
                ? "Parsed " + hits + " important field(s) using exact/neighbor/fallback passes."
                : "This file may not be a Form 1040; values are best-effort only.");
        return s;
    }

    private static void appendKeyLineWarnings(List<String> warnings, FieldResult result, String label) {
        if (result == null || result.amount() == null) {
            warnings.add(label + " could not be extracted confidently.");
            return;
        }
        if ("fallback".equals(result.pass())) {
            warnings.add(label + " inferred via fallback parsing.");
        }
    }

    private static void putProvenance(
            Map<String, Form1040FieldProvenance> target,
            String field,
            FieldResult fr,
            String note) {
        if (fr == null) {
            return;
        }
        String conf = switch (fr.pass()) {
            case "exact" -> "HIGH";
            case "neighbor" -> "MEDIUM";
            default -> "LOW";
        };
        target.put(field, new Form1040FieldProvenance(fr.pass(), fr.tokens(), conf, note));
    }

    private static FieldResult parseField(
            String[] lines,
            String fullText,
            String lineToken,
            List<String> strictKeywords,
            List<String> fallbackKeywords) {
        BigDecimal exact = amountForLine(lines, lineToken, strictKeywords);
        if (exact != null) {
            return new FieldResult(exact, "exact", strictKeywords);
        }
        BigDecimal neighbor = amountNearLine(lines, strictKeywords);
        if (neighbor != null) {
            return new FieldResult(neighbor, "neighbor", strictKeywords);
        }
        BigDecimal fallback = firstMoneyNear(fullText, fallbackKeywords);
        return new FieldResult(fallback, "fallback", fallbackKeywords);
    }

    private static BigDecimal bestAmount(String[] lines, String fullText, List<String> keywords) {
        BigDecimal n = amountNearLine(lines, keywords);
        if (n != null) {
            return n;
        }
        return firstMoneyNear(fullText, keywords);
    }

    private static BigDecimal amountForLine(String[] lines, String lineToken, List<String> keywords) {
        if (lines == null || lines.length == 0) {
            return null;
        }
        BigDecimal lineTokenNumber = numericLineToken(lineToken);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i];
            String up = line.toUpperCase(Locale.ROOT);
            if (!containsLineToken(up, lineToken)) {
                continue;
            }
            if (!containsAny(up, keywords)) {
                continue;
            }
            List<BigDecimal> same = stripLineNumberAmount(moneyTokens(line), lineTokenNumber);
            if (!same.isEmpty()) {
                return same.get(same.size() - 1).setScale(2, RoundingMode.HALF_UP);
            }
            for (int j = i + 1; j <= Math.min(lines.length - 1, i + 2); j++) {
                String nextLine = lines[j] == null ? "" : lines[j];
                String nextUpper = nextLine.toUpperCase(Locale.ROOT);
                if (isDifferentLineLabel(nextUpper, lineToken)) {
                    break;
                }
                List<BigDecimal> next = moneyTokens(nextLine);
                if (!next.isEmpty()) {
                    return next.get(next.size() - 1).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        return null;
    }

    private static List<BigDecimal> stripLineNumberAmount(List<BigDecimal> amounts, BigDecimal lineTokenNumber) {
        if (amounts == null || amounts.isEmpty() || lineTokenNumber == null) {
            return amounts;
        }
        List<BigDecimal> filtered = new ArrayList<>();
        for (BigDecimal amount : amounts) {
            if (amount == null || amount.compareTo(lineTokenNumber) == 0) {
                continue;
            }
            filtered.add(amount);
        }
        return filtered;
    }

    private static BigDecimal numericLineToken(String lineToken) {
        if (lineToken == null || lineToken.isBlank()) {
            return null;
        }
        String tokenDigits = lineToken.replaceAll("[^0-9]", "");
        if (tokenDigits.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(tokenDigits).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal amountNearLine(String[] lines, List<String> keywords) {
        if (lines == null || lines.length == 0) {
            return null;
        }
        int bestIdx = -1;
        int bestScore = 0;
        for (int i = 0; i < lines.length; i++) {
            String up = lines[i] == null ? "" : lines[i].toUpperCase(Locale.ROOT);
            int score = 0;
            for (String kw : keywords) {
                if (up.contains(kw.toUpperCase(Locale.ROOT))) {
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
        for (int i = bestIdx; i <= Math.min(lines.length - 1, bestIdx + 3); i++) {
            String line = lines[i] == null ? "" : lines[i];
            if (i > bestIdx && isDifferentLineLabel(line.toUpperCase(Locale.ROOT), null)) {
                break;
            }
            List<BigDecimal> candidates = moneyTokens(line);
            if (!candidates.isEmpty()) {
                return candidates.get(candidates.size() - 1).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    private static boolean isDifferentLineLabel(String upperLine, String expectedToken) {
        Matcher m = GENERIC_LINE_LABEL.matcher(upperLine == null ? "" : upperLine);
        if (!m.find()) {
            return false;
        }
        if (expectedToken == null || expectedToken.isBlank()) {
            return true;
        }
        String expected = expectedToken.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        String found = m.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return !found.equals(expected);
    }

    private static BigDecimal firstMoneyNear(String text, List<String> keywords) {
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

    private static boolean containsAny(String upper, List<String> keywords) {
        for (String kw : keywords) {
            if (upper.contains(kw.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLineToken(String upper, String token) {
        String normalized = upper == null ? "" : upper;
        String t = token.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return switch (t) {
            case "1A" -> LINE_1A_TOKEN.matcher(normalized).find();
            case "24" -> LINE_24_TOKEN.matcher(normalized).find()
                    && !normalized.contains("2A")
                    && !normalized.contains("LINE 2A");
            case "37" -> LINE_37_TOKEN.matcher(normalized).find();
            default -> normalized.replaceAll("\\s+", "").contains(t);
        };
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
                // ignore and continue
            }
        }
        return null;
    }

    private static String detectFilingStatus(String upper) {
        if (upper.contains("QUALIFYING SURVIVING SPOUSE")) return "Qualifying surviving spouse";
        if (upper.contains("HEAD OF HOUSEHOLD")) return "Head of household";
        if (upper.contains("MARRIED FILING SEPARATELY")) return "Married filing separately";
        if (upper.contains("MARRIED FILING JOINTLY")) return "Married filing jointly";
        if (upper.contains("MARRIED FILING")) return "Married filing (unspecified)";
        if (upper.contains("SINGLE") && upper.contains("FILING STATUS")) return "Single";
        if (upper.contains("\nSINGLE") || upper.contains(" SINGLE ")) return "Single";
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

    private record FieldResult(BigDecimal amount, String pass, List<String> tokens) {}
}
