package com.svp.tracker.finance.service.banking;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Best-effort parsers for common banking export shapes. Institution-specific quirks may require follow-up imports.
 */
public final class BankingFormatParser {

    private static final Pattern OFX_STMT =
            Pattern.compile("<STMTTRN>\\s*([\\s\\S]*?)</STMTTRN>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFX_DT = Pattern.compile("<DTPOSTED>\\s*([^<\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFX_AMT = Pattern.compile("<TRNAMT>\\s*([^<\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFX_NAME = Pattern.compile("<NAME>\\s*([^<]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFX_MEMO = Pattern.compile("<MEMO>\\s*([^<]*)", Pattern.CASE_INSENSITIVE);

    private BankingFormatParser() {}

    public static BankingParseOutcome parse(byte[] raw, String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        String ext = extension(name);
        return switch (ext) {
            case "pdf" -> new BankingParseOutcome(List.of(), "PDF stored for reference; transaction parsing not applied.");
            case "csv" -> parseCsv(raw);
            case "qif" -> parseQif(raw);
            case "qfx", "ofx", "qbo" -> parseOfxFamily(raw, ext);
            case "xls", "xlsx" -> parseExcel(raw);
            default -> new BankingParseOutcome(List.of(), "Unsupported extension ." + ext + "; save as CSV, QIF, QFX, OFX, QBO, or Excel.");
        };
    }

    public static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static BankingParseOutcome parseCsv(byte[] raw) {
        Charset cs = sniffCharset(raw);
        String text = new String(raw, cs);
        String[] lines = text.split("\\R");
        if (lines.length == 0) {
            return new BankingParseOutcome(List.of(), "Empty CSV.");
        }
        String headerLine = lines[0];
        char delim = headerLine.contains("\t") ? '\t' : ',';
        String[] headers = splitCsvLine(headerLine, delim);
        Map<String, Integer> col = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            col.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
        }
        Integer dateIdx = findColumn(
                col,
                "date",
                "posted",
                "post date",
                "transaction date",
                "trans date",
                "dtposted");
        Integer amtIdx = findColumn(col, "amount", "amt", "value", "debit", "credit", "transaction amount");
        Integer descIdx = findColumn(
                col, "description", "memo", "payee", "details", "name", "narration", "merchant", "note");
        Integer debitIdx = findColumn(col, "debit");
        Integer creditIdx = findColumn(col, "credit");
        if (dateIdx == null || (amtIdx == null && (debitIdx == null || creditIdx == null))) {
            return new BankingParseOutcome(
                    List.of(),
                    "CSV headers must include a date column and an amount (or separate debit/credit columns). Found: "
                            + String.join(", ", col.keySet()));
        }
        List<BankingParsedRow> out = new ArrayList<>();
        int skipped = 0;
        for (int r = 1; r < lines.length; r++) {
            String line = lines[r].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cells = splitCsvLine(line, delim);
            try {
                LocalDate d = parseFlexibleDate(cellAt(cells, dateIdx));
                BigDecimal amt;
                if (amtIdx != null) {
                    amt = parseAmount(cellAt(cells, amtIdx));
                } else {
                    BigDecimal deb = parseAmountOrZero(cellAt(cells, debitIdx));
                    BigDecimal cred = parseAmountOrZero(cellAt(cells, creditIdx));
                    amt = cred.subtract(deb);
                }
                String desc = descIdx != null ? cellAt(cells, descIdx) : "";
                out.add(new BankingParsedRow(d, amt, desc == null ? "" : desc.trim()));
            } catch (RuntimeException ex) {
                skipped++;
            }
        }
        String note = skipped > 0 ? skipped + " row(s) skipped due to parse errors." : "";
        return new BankingParseOutcome(out, note);
    }

    private static BankingParseOutcome parseQif(byte[] raw) {
        Charset cs = sniffCharset(raw);
        List<String> lines = new ArrayList<>();
        for (String ln : new String(raw, cs).split("\\R")) {
            lines.add(ln);
        }
        List<BankingParsedRow> out = new ArrayList<>();
        LocalDate curDate = null;
        BigDecimal curAmt = null;
        StringBuilder payee = new StringBuilder();
        StringBuilder memo = new StringBuilder();
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            char c0 = line.charAt(0);
            String rest = line.length() > 1 ? line.substring(1) : "";
            if (c0 == '^') {
                if (curDate != null && curAmt != null) {
                    String desc = (payee.length() > 0 ? payee : memo).toString().trim();
                    if (desc.isEmpty() && memo.length() > 0) {
                        desc = memo.toString().trim();
                    }
                    out.add(new BankingParsedRow(curDate, curAmt, desc));
                }
                curDate = null;
                curAmt = null;
                payee.setLength(0);
                memo.setLength(0);
            } else if (c0 == 'D') {
                curDate = parseFlexibleDate(rest.trim());
            } else if (c0 == 'T') {
                curAmt = parseAmount(rest.trim());
            } else if (c0 == 'P') {
                if (!payee.isEmpty()) {
                    payee.append(' ');
                }
                payee.append(rest.trim());
            } else if (c0 == 'M') {
                if (!memo.isEmpty()) {
                    memo.append(' ');
                }
                memo.append(rest.trim());
            }
        }
        if (curDate != null && curAmt != null) {
            String desc = (payee.length() > 0 ? payee : memo).toString().trim();
            if (desc.isEmpty() && memo.length() > 0) {
                desc = memo.toString().trim();
            }
            out.add(new BankingParsedRow(curDate, curAmt, desc));
        }
        return new BankingParseOutcome(out, "");
    }

    private static BankingParseOutcome parseOfxFamily(byte[] raw, String ext) {
        String s = new String(raw, StandardCharsets.UTF_8);
        if (!s.contains("OFX") && !s.contains("ofx") && ext.equals("qbo")) {
            return new BankingParseOutcome(List.of(), "QBO file did not contain recognizable OFX/QFX content.");
        }
        Matcher m = OFX_STMT.matcher(s);
        List<BankingParsedRow> rows = new ArrayList<>();
        while (m.find()) {
            String block = m.group(1);
            String dt = first(OFX_DT, block);
            String amt = first(OFX_AMT, block);
            if (dt == null || amt == null) {
                continue;
            }
            try {
                LocalDate d = parseOfxDate(dt.trim());
                BigDecimal a = new BigDecimal(amt.trim());
                String name = nz(first(OFX_NAME, block));
                String memo = nz(first(OFX_MEMO, block));
                String desc = (name + " " + memo).trim();
                rows.add(new BankingParsedRow(d, a, desc));
            } catch (RuntimeException ignored) {
                // skip bad block
            }
        }
        String note = rows.isEmpty() ? "No STMTTRN blocks parsed; file may use an unsupported OFX variant." : "";
        return new BankingParseOutcome(rows, note);
    }

    private static BankingParseOutcome parseExcel(byte[] raw) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(raw))) {
            Sheet sh = wb.getSheetAt(0);
            if (sh == null) {
                return new BankingParseOutcome(List.of(), "Workbook has no sheets.");
            }
            DataFormatter fmt = new DataFormatter();
            int firstRow = sh.getFirstRowNum();
            Row header = sh.getRow(firstRow);
            if (header == null) {
                return new BankingParseOutcome(List.of(), "Empty sheet.");
            }
            Map<String, Integer> col = new LinkedHashMap<>();
            short firstCell = header.getFirstCellNum();
            short lastCell = header.getLastCellNum();
            if (firstCell < 0 || lastCell < 0) {
                return new BankingParseOutcome(List.of(), "Header row has no cells.");
            }
            for (int c = firstCell; c < lastCell; c++) {
                Cell cell = header.getCell(c);
                if (cell != null) {
                    String h = fmt.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
                    if (!h.isEmpty()) {
                        col.put(h, (int) c);
                    }
                }
            }
            Integer dateIdx = findColumn(
                    col,
                    "date",
                    "posted",
                    "post date",
                    "transaction date",
                    "trans date",
                    "dtposted");
            Integer amtIdx = findColumn(col, "amount", "amt", "value", "debit", "credit", "transaction amount");
            Integer descIdx = findColumn(
                    col, "description", "memo", "payee", "details", "name", "narration", "merchant", "note");
            Integer debitIdx = findColumn(col, "debit");
            Integer creditIdx = findColumn(col, "credit");
            if (dateIdx == null || (amtIdx == null && (debitIdx == null || creditIdx == null))) {
                return new BankingParseOutcome(
                        List.of(),
                        "First row must be headers with date and amount (or debit/credit). Found: "
                                + String.join(", ", col.keySet()));
            }
            List<BankingParsedRow> out = new ArrayList<>();
            int skipped = 0;
            for (int r = sh.getFirstRowNum() + 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row == null) {
                    continue;
                }
                try {
                    String ds = fmt.formatCellValue(row.getCell(dateIdx)).trim();
                    if (ds.isEmpty()) {
                        continue;
                    }
                    LocalDate d = parseFlexibleDate(ds);
                    BigDecimal amt;
                    if (amtIdx != null) {
                        amt = parseAmount(fmt.formatCellValue(row.getCell(amtIdx)).trim());
                    } else {
                        BigDecimal deb = parseAmountOrZero(fmt.formatCellValue(row.getCell(debitIdx)).trim());
                        BigDecimal cred = parseAmountOrZero(fmt.formatCellValue(row.getCell(creditIdx)).trim());
                        amt = cred.subtract(deb);
                    }
                    String desc = descIdx != null ? fmt.formatCellValue(row.getCell(descIdx)).trim() : "";
                    out.add(new BankingParsedRow(d, amt, desc));
                } catch (RuntimeException ex) {
                    skipped++;
                }
            }
            String note = skipped > 0 ? skipped + " Excel row(s) skipped." : "";
            return new BankingParseOutcome(out, note);
        } catch (IOException e) {
            return new BankingParseOutcome(List.of(), "Excel read failed: " + e.getMessage());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String first(Pattern p, String block) {
        Matcher mm = p.matcher(block);
        return mm.find() ? mm.group(1) : null;
    }

    private static LocalDate parseOfxDate(String v) {
        String digits = v.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            String ymd = digits.substring(0, 8);
            return LocalDate.parse(ymd, DateTimeFormatter.BASIC_ISO_DATE);
        }
        throw new DateTimeParseException("ofx date", v, 0);
    }

    private static Charset sniffCharset(byte[] raw) {
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.UTF_8;
    }

    private static String[] splitCsvLine(String line, char delim) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQ = !inQ;
            } else if (c == delim && !inQ) {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cells.add(cur.toString());
        return cells.toArray(String[]::new);
    }

    private static String cellAt(String[] cells, int idx) {
        if (idx < 0 || idx >= cells.length) {
            return "";
        }
        return cells[idx].trim();
    }

    private static Integer findColumn(Map<String, Integer> col, String... names) {
        for (String n : names) {
            Integer i = col.get(n);
            if (i != null) {
                return i;
            }
        }
        for (Map.Entry<String, Integer> e : col.entrySet()) {
            for (String n : names) {
                if (e.getKey().contains(n)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static LocalDate parseFlexibleDate(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new DateTimeParseException("empty", raw, 0);
        }
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        DateTimeFormatter[] fmts = {
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        };
        for (DateTimeFormatter f : fmts) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // next
            }
        }
        throw new DateTimeParseException("date", raw, 0);
    }

    private static BigDecimal parseAmount(String raw) {
        String s = raw.replace("$", "").replace(",", "").trim();
        if (s.isEmpty()) {
            throw new NumberFormatException("empty amount");
        }
        return new BigDecimal(s);
    }

    private static BigDecimal parseAmountOrZero(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        return parseAmount(raw);
    }
}
