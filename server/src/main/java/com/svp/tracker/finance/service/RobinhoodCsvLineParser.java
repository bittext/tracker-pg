package com.svp.tracker.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CSV line parsing helpers for {@link RobinhoodCsvImportService}: column normalization when Robinhood exports extra
 * commas (description or unquoted {@code $4,799.73} amounts), plus optional quantity/price/amount consistency checks.
 */
final class RobinhoodCsvLineParser {

    private static final Pattern OPTION_DESC = Pattern.compile("(?i)\\b(call|put)\\b");
    private static final BigDecimal OPTION_MULTIPLIER = new BigDecimal("100");
    private static final BigDecimal RATIO_LOW = new BigDecimal("0.85");
    private static final BigDecimal RATIO_HIGH = new BigDecimal("1.15");

    private RobinhoodCsvLineParser() {}

    /**
     * Robinhood exports may contain unquoted commas in Description or Amount. When a row has extra columns, rebuild
     * using fixed columns from both ends:
     *
     * <pre>
     * [date, process, settle, instrument, ...description..., transCode, quantity, price, amount]
     * </pre>
     */
    static List<String> normalizeColumns(List<String> cols, int expected) {
        if (expected != 9 || cols.size() <= expected) {
            return cols;
        }
        int amountStart = findAmountStartIndex(cols);
        if (amountStart < 5) {
            return legacyNormalizeColumns(cols);
        }
        String amount = String.join(",", cols.subList(amountStart, cols.size()));
        int transIdx = amountStart - 3;
        int qtyIdx = amountStart - 2;
        int priceIdx = amountStart - 1;
        if (transIdx < 5) {
            return legacyNormalizeColumns(cols);
        }
        String activity = cols.get(0);
        String process = cols.get(1);
        String settle = cols.get(2);
        String instrument = cols.get(3);
        String description = String.join(",", cols.subList(4, transIdx));
        return List.of(
                activity,
                process,
                settle,
                instrument,
                description,
                cols.get(transIdx),
                cols.get(qtyIdx),
                cols.get(priceIdx),
                amount);
    }

    /** Previous tail logic when amount merge cannot find a stable boundary. */
    private static List<String> legacyNormalizeColumns(List<String> cols) {
        String activity = cols.get(0);
        String process = cols.get(1);
        String settle = cols.get(2);
        String instrument = cols.get(3);
        String transCode = cols.get(cols.size() - 4);
        String quantity = cols.get(cols.size() - 3);
        String price = cols.get(cols.size() - 2);
        String amount = cols.get(cols.size() - 1);
        String description = String.join(",", cols.subList(4, cols.size() - 4));
        return List.of(activity, process, settle, instrument, description, transCode, quantity, price, amount);
    }

    /**
     * When Amount is unquoted ({@code $4,799.73}), the comma splits the last field into {@code $4} + {@code 799.73}.
     * Walk backward from the end while cells look like leading fragments of a split amount.
     */
    static int findAmountStartIndex(List<String> cols) {
        int start = cols.size() - 1;
        while (start > 5 && looksLikeLeadingAmountChunk(cols.get(start - 1))) {
            start--;
        }
        return start;
    }

    /**
     * Leading fragment of a comma-split amount (e.g. {@code $4} from {@code $4,799.73}, {@code -$1} from
     * {@code -$1,146.08}). Not a full decimal amount.
     */
    static boolean looksLikeLeadingAmountChunk(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty() || t.contains(".")) {
            return false;
        }
        return t.matches("-?\\$?\\d{1,3}");
    }

    /**
     * Warn when option cash does not match quantity × price × 100 (catches shifted columns after comma splits).
     */
    static void addOptionCashMismatchWarning(
            List<String> errors, int lineNo, String description, BigDecimal quantity, BigDecimal price, BigDecimal amount) {
        if (quantity == null || price == null || amount == null) {
            return;
        }
        if (description == null || !OPTION_DESC.matcher(description).find()) {
            return;
        }
        if (quantity.abs().compareTo(BigDecimal.ZERO) == 0 || price.abs().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal implied = quantity.abs().multiply(price.abs()).multiply(OPTION_MULTIPLIER);
        if (implied.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal ratio = amount.abs().divide(implied, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(RATIO_LOW) >= 0 && ratio.compareTo(RATIO_HIGH) <= 0) {
            return;
        }
        BigDecimal fromCash =
                amount
                        .abs()
                        .divide(price.abs().multiply(OPTION_MULTIPLIER), 2, RoundingMode.HALF_UP);
        errors.add(
                "Line "
                        + lineNo
                        + ": quantity ("
                        + quantity.stripTrailingZeros().toPlainString()
                        + ") × price ("
                        + price.stripTrailingZeros().toPlainString()
                        + ") does not match amount ("
                        + amount.stripTrailingZeros().toPlainString()
                        + ") for option; cash implies about "
                        + fromCash.stripTrailingZeros().toPlainString()
                        + " contract(s). Check for unquoted commas in the Amount column.");
    }

    /** Minimal CSV parser supporting quoted fields and escaped quotes. */
    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (c == ',' && !quoted) {
                out.add(cell.toString());
                cell.setLength(0);
                continue;
            }
            cell.append(c);
        }
        out.add(cell.toString());
        return out;
    }

    static String normHeader(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return s.replace('_', ' ').replaceAll("\\s+", " ");
    }
}
