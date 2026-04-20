package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.RobinhoodCsvDirectoryImportDto;
import com.svp.tracker.finance.dto.RobinhoodCsvDirectoryImportFileDto;
import com.svp.tracker.finance.dto.RobinhoodCsvImportResultDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Safe local Robinhood CSV import:
 *
 * <ul>
 *   <li>Credential-free (manual export only)
 *   <li>Dry-run by default
 *   <li>Parameterized SQL insert when apply=true
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodCsvImportService {

    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#]{0,127}$");
    private static final List<String> TARGET_COLUMNS =
            List.of(
                    "ACTIVITY_DATE",
                    "PROCESS_DATE",
                    "SETTLE_DATE",
                    "INSTRUMENT",
                    "DESCRIPTION",
                    "TRANS_CODE",
                    "QUANTITY",
                    "PRICE",
                    "AMOUNT");

    private final JdbcTemplate jdbcTemplate;
    private final FinanceProperties props;

    public RobinhoodCsvImportResultDto importCsv(MultipartFile file, boolean apply) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }
        if (file.getSize() > props.maxImportCsvBytes()) {
            throw new IllegalArgumentException(
                    "CSV file exceeds max-import-csv-bytes=" + props.maxImportCsvBytes());
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read CSV file", e);
        }
        return importCsvBytes(raw, file.getOriginalFilename(), apply);
    }

    /**
     * Imports every {@code *.csv} file from {@link FinanceProperties#robinhoodCsvImportDirectory()} in sorted file-name
     * order. When {@code apply} is true, files that complete with {@link RobinhoodCsvImportResultDto#errorCount()} ==
     * {@code 0} are moved to {@link FinanceProperties#robinhoodCsvUploadedDirectory()}.
     */
    public RobinhoodCsvDirectoryImportDto importAllCsvFromConfiguredDirectory(boolean apply) {
        String inDir = props.robinhoodCsvImportDirectory();
        String upDir = props.robinhoodCsvUploadedDirectory();
        if (inDir.isBlank()) {
            throw new IllegalArgumentException(
                    "Configure tracker.finance.robinhood-csv-import-directory for directory import");
        }
        if (apply && upDir.isBlank()) {
            throw new IllegalArgumentException(
                    "Configure tracker.finance.robinhood-csv-uploaded-directory when apply=true (destination for moved "
                            + "files)");
        }
        Path importDir = Path.of(inDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(importDir)) {
            throw new IllegalArgumentException("Import directory does not exist or is not a directory: " + importDir);
        }
        Path uploadedDir = upDir.isBlank() ? null : Path.of(upDir).toAbsolutePath().normalize();

        List<Path> csvFiles;
        try (Stream<Path> stream = Files.list(importDir)) {
            csvFiles =
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                            .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list import directory: " + importDir, e);
        }
        if (csvFiles.size() > props.maxDirectoryImportFiles()) {
            throw new IllegalArgumentException(
                    "Too many CSV files in import directory ("
                            + csvFiles.size()
                            + "). Max is tracker.finance.max-directory-import-files="
                            + props.maxDirectoryImportFiles());
        }

        List<RobinhoodCsvDirectoryImportFileDto> out = new ArrayList<>();
        for (Path path : csvFiles) {
            String name = path.getFileName().toString();
            RobinhoodCsvImportResultDto result;
            try {
                byte[] raw = Files.readAllBytes(path);
                result = importCsvBytes(raw, name, apply);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.warn("CSV import failed for {}: {}", name, e.getMessage());
                result = failedImportResult(apply, name, e);
            } catch (IOException e) {
                log.warn("Could not read {}", path, e);
                result = failedImportResult(apply, name, e);
            }

            String movedTo = null;
            if (apply && result.errorCount() == 0 && uploadedDir != null) {
                try {
                    Files.createDirectories(uploadedDir);
                    Path dest = uploadedDir.resolve(name);
                    Files.move(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    movedTo = dest.toAbsolutePath().toString();
                } catch (IOException e) {
                    log.error("Could not move {} to {}", path, uploadedDir, e);
                    result =
                            augmentWithMoveFailure(
                                    result,
                                    "Imported successfully but failed to move file to uploaded directory: "
                                            + oneLineMessage(e.getMessage()));
                    movedTo = null;
                }
            }
            out.add(new RobinhoodCsvDirectoryImportFileDto(movedTo, result));
        }

        String note =
                apply
                        ? "Processed "
                                + csvFiles.size()
                                + " file(s). Successful files with no row errors were moved to the uploaded directory."
                        : "Dry-run: no database writes and no files moved. Set apply=true to insert and move completed "
                                + "files.";

        return new RobinhoodCsvDirectoryImportDto(
                apply, importDir.toString(), upDir.isBlank() ? "" : uploadedDir.toString(), csvFiles.size(), out, note);
    }

    private RobinhoodCsvImportResultDto failedImportResult(boolean apply, String fileName, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new RobinhoodCsvImportResultDto(
                apply,
                fileName,
                0,
                0,
                0,
                0,
                0,
                1,
                List.of(msg),
                List.of(),
                List.of(),
                List.of(),
                props.robinhoodTable(),
                "Import failed before or during row processing.");
    }

    private static RobinhoodCsvImportResultDto augmentWithMoveFailure(RobinhoodCsvImportResultDto base, String extra) {
        List<String> errs = new ArrayList<>(base.errors());
        errs.add(extra);
        return new RobinhoodCsvImportResultDto(
                base.apply(),
                base.fileName(),
                base.csvRowCount(),
                base.parsedRows(),
                base.insertedRows(),
                base.duplicateRowsSkipped(),
                base.skippedRows(),
                errs.size(),
                errs,
                base.detectedHeaders(),
                base.detectedInstruments(),
                base.previewRows(),
                base.tableTarget(),
                base.note());
    }

    /**
     * Parses and optionally persists CSV content. {@code raw} is UTF-8 text (same encoding as {@link #importCsv}).
     */
    public RobinhoodCsvImportResultDto importCsvBytes(byte[] raw, String fileName, boolean apply) {
        if (raw == null) {
            throw new IllegalArgumentException("CSV content is required");
        }
        if (raw.length > props.maxImportCsvBytes()) {
            throw new IllegalArgumentException(
                    "CSV file exceeds max-import-csv-bytes=" + props.maxImportCsvBytes());
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "upload.csv";
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        List<String> records = splitCsvRecords(text);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        List<String> headerRow = parseCsvLine(records.get(0));
        Map<String, Integer> headerIdx = indexHeaders(headerRow);

        List<String> errors = new ArrayList<>();
        List<Map<String, String>> preview = new ArrayList<>();
        Set<String> instruments = new LinkedHashSet<>();
        int parsed = 0;
        int skipped = 0;
        int inserted = 0;
        int duplicateRowsSkipped = 0;
        int csvRows = 0;
        Set<String> dedupeSeen = props.importDeduplicate() ? new HashSet<>() : null;

        for (int i = 1; i < records.size(); i++) {
            String record = records.get(i);
            if (record == null || record.trim().isEmpty()) {
                continue;
            }
            csvRows++;
            List<String> cols = normalizeColumns(parseCsvLine(record), headerRow.size());
            ParsedRow row = mapRow(cols, headerIdx, i + 1, errors);
            if (row == null) {
                skipped++;
                if (errors.size() >= props.maxImportErrors()) {
                    break;
                }
                continue;
            }
            parsed++;
            if (!row.instrument.isBlank()) {
                instruments.add(row.instrument);
            }
            if (preview.size() < props.importPreviewRows()) {
                preview.add(row.asMap());
            }
            if (props.importDeduplicate()) {
                String key = dedupeKey(row);
                if (dedupeSeen.contains(key)) {
                    duplicateRowsSkipped++;
                    continue;
                }
                if (rowExistsInTable(row)) {
                    duplicateRowsSkipped++;
                    dedupeSeen.add(key);
                    continue;
                }
            }
            if (apply) {
                try {
                    insertRow(row);
                    inserted++;
                    if (props.importDeduplicate()) {
                        dedupeSeen.add(dedupeKey(row));
                    }
                } catch (DataAccessException e) {
                    skipped++;
                    if (errors.size() < props.maxImportErrors()) {
                        errors.add(
                                "Line "
                                        + row.lineNo
                                        + ": DB insert failed ("
                                        + oneLineMessage(e.getMessage())
                                        + ")");
                    }
                }
            } else if (props.importDeduplicate()) {
                dedupeSeen.add(dedupeKey(row));
            }
        }

        String note =
                apply
                        ? "Rows were inserted into "
                                + props.robinhoodTable()
                                + (props.importDeduplicate()
                                        ? " (deduplication on: same row in file or table is skipped)."
                                        : "")
                                + " Keep this endpoint local-only and use exported CSV files."
                        : "Dry-run only. Set apply=true to insert parsed rows into "
                                + props.robinhoodTable()
                                + (props.importDeduplicate()
                                        ? " (deduplication on: duplicate rows are counted in duplicateRowsSkipped)."
                                        : "")
                                + ".";

        return new RobinhoodCsvImportResultDto(
                apply,
                fileName,
                csvRows,
                parsed,
                inserted,
                duplicateRowsSkipped,
                skipped,
                errors.size(),
                errors,
                headerRow,
                new ArrayList<>(instruments),
                preview,
                props.robinhoodTable(),
                note);
    }

    /**
     * Robinhood exports may contain unquoted commas in Description. When a row has extra columns, rebuild using fixed
     * columns from both ends:
     *
     * <pre>
     * [date, process, settle, instrument, ...description..., transCode, quantity, price, amount]
     * </pre>
     */
    private static List<String> normalizeColumns(List<String> cols, int expected) {
        if (cols.size() == expected || expected != 9) {
            return cols;
        }
        if (cols.size() < expected) {
            return cols;
        }
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

    private static String oneLineMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown error";
        }
        String s = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return s.length() > 220 ? s.substring(0, 217) + "..." : s;
    }

    private ParsedRow mapRow(
            List<String> cols, Map<String, Integer> headerIdx, int lineNo, List<String> errors) {
        String activityRaw = cell(cols, headerIdx, "activity_date", "activity date", "date");
        String processRaw = cell(cols, headerIdx, "process_date", "process date");
        String settleRaw = cell(cols, headerIdx, "settle_date", "settle date");
        Timestamp activity = parseDateCell(activityRaw, lineNo, errors, "activity date");
        Timestamp process = parseDateCell(processRaw, lineNo, errors, "process date");
        Timestamp settle = parseDateCell(settleRaw, lineNo, errors, "settle date");
        String instrument = cell(cols, headerIdx, "instrument", "symbol", "ticker");
        String description = cell(cols, headerIdx, "description");
        String transCode = cell(cols, headerIdx, "trans_code", "trans code", "type");
        // Ignore non-transaction footer lines (e.g., CSV disclaimer paragraphs).
        if (blank(activityRaw)
                && blank(processRaw)
                && blank(settleRaw)
                && blank(instrument)
                && blank(transCode)) {
            return null;
        }
        BigDecimal quantity = decimalCell(cols, headerIdx, errors, lineNo, true, "quantity", "qty");
        BigDecimal price = decimalCell(cols, headerIdx, errors, lineNo, false, "price");
        BigDecimal amount = decimalCell(cols, headerIdx, errors, lineNo, false, "amount", "net amount");

        boolean allBlank =
                blank(activityRaw)
                        && blank(processRaw)
                        && blank(settleRaw)
                        && blank(instrument)
                        && blank(description)
                        && blank(transCode)
                        && quantity == null
                        && price == null
                        && amount == null;
        if (allBlank) {
            return null;
        }
        return new ParsedRow(
                lineNo, activity, process, settle, instrument, description, transCode, quantity, price, amount);
    }

    /**
     * Stable key for in-file deduplication (aligned with {@link #rowExistsInTable(ParsedRow)} null / trim / numeric
     * semantics).
     */
    private static String dedupeKey(ParsedRow row) {
        return String.join(
                "\u0000",
                tsMillis(row.activityDate),
                tsMillis(row.processDate),
                tsMillis(row.settleDate),
                strDedupeKey(row.instrument),
                strDedupeKey(row.description),
                strDedupeKey(row.transCode),
                numPlain(row.quantity),
                numPlain(row.price),
                numPlain(row.amount));
    }

    private static String tsMillis(Timestamp t) {
        return t == null ? "\0" : String.valueOf(t.getTime());
    }

    private static String numPlain(BigDecimal b) {
        return b == null ? "\0" : b.stripTrailingZeros().toPlainString();
    }

    /** Matches {@link #trimOrNull(String)} semantics for DB binding (blank → absent). */
    private static String strDedupeKey(String x) {
        String t = trimOrNull(x);
        return t == null ? "\0" : t;
    }

    /**
     * Null-safe match on all persisted columns (Oracle {@code =} does not treat two NULLs as equal).
     */
    private boolean rowExistsInTable(ParsedRow row) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(qualifiedTable()).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();

        appendNullSafeMatch(sql, args, "ACTIVITY_DATE", row.activityDate);
        appendNullSafeMatch(sql, args, "PROCESS_DATE", row.processDate);
        appendNullSafeMatch(sql, args, "SETTLE_DATE", row.settleDate);
        appendNullSafeMatch(sql, args, "TRIM(INSTRUMENT)", trimOrNull(row.instrument));
        appendNullSafeMatch(sql, args, "TRIM(DESCRIPTION)", trimOrNull(row.description));
        appendNullSafeMatch(sql, args, "TRIM(TRANS_CODE)", trimOrNull(row.transCode));
        appendNullSafeMatch(sql, args, "QUANTITY", row.quantity);
        appendNullSafeMatch(sql, args, "PRICE", row.price);
        appendNullSafeMatch(sql, args, "AMOUNT", row.amount);

        Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n != null && n > 0;
    }

    private static void appendNullSafeMatch(StringBuilder sql, List<Object> args, String expression, Object value) {
        if (value == null) {
            sql.append(" AND ").append(expression).append(" IS NULL");
            return;
        }
        sql.append(" AND ").append(expression).append(" = ?");
        args.add(value);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void insertRow(ParsedRow row) {
        String table = qualifiedTable();
        String sql =
                "INSERT INTO "
                        + table
                        + " ("
                        + String.join(", ", TARGET_COLUMNS)
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(
                sql,
                ps -> {
                    bindTimestamp(ps, 1, row.activityDate);
                    bindTimestamp(ps, 2, row.processDate);
                    bindTimestamp(ps, 3, row.settleDate);
                    bindString(ps, 4, row.instrument);
                    bindString(ps, 5, row.description);
                    bindString(ps, 6, row.transCode);
                    bindDecimal(ps, 7, row.quantity);
                    bindDecimal(ps, 8, row.price);
                    bindDecimal(ps, 9, row.amount);
                });
    }

    private static void bindString(java.sql.PreparedStatement ps, int idx, String val) throws java.sql.SQLException {
        if (blank(val)) {
            ps.setNull(idx, java.sql.Types.VARCHAR);
            return;
        }
        ps.setString(idx, val.trim());
    }

    private static void bindDecimal(java.sql.PreparedStatement ps, int idx, BigDecimal val)
            throws java.sql.SQLException {
        if (val == null) {
            ps.setNull(idx, java.sql.Types.NUMERIC);
            return;
        }
        ps.setBigDecimal(idx, val);
    }

    private static void bindTimestamp(java.sql.PreparedStatement ps, int idx, Timestamp val)
            throws java.sql.SQLException {
        if (val == null) {
            ps.setNull(idx, java.sql.Types.TIMESTAMP);
            return;
        }
        ps.setTimestamp(idx, val);
    }

    private static Timestamp parseDateCell(String raw, int lineNo, List<String> errors, String label) {
        if (blank(raw)) {
            return null;
        }
        Timestamp ts = parseTimestampLoose(raw);
        if (ts != null) {
            return ts;
        }
        errors.add("Line " + lineNo + ": invalid " + label + " '" + raw + "'");
        return null;
    }

    private static Timestamp parseTimestampLoose(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            LocalDate d = tryParseLocalDate(s);
            if (d != null) {
                return Timestamp.valueOf(d.atStartOfDay());
            }
            String token = firstDateToken(s);
            if (!token.equals(s)) {
                d = tryParseLocalDate(token);
                if (d != null) {
                    return Timestamp.valueOf(d.atStartOfDay());
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return null;
    }

    private static LocalDate tryParseLocalDate(String day) {
        List<DateTimeFormatter> fmts =
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE,
                        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
                        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
                        DateTimeFormatter.ofPattern("MM/dd/yy", Locale.US),
                        DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
                        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US));
        for (DateTimeFormatter f : fmts) {
            try {
                return LocalDate.parse(day, f);
            } catch (RuntimeException ignored) {
                // try next format
            }
        }
        return null;
    }

    private static String firstDateToken(String raw) {
        int space = raw.indexOf(' ');
        int t = raw.indexOf('T');
        int cut = -1;
        if (space >= 0) {
            cut = space;
        }
        if (t >= 0 && (cut < 0 || t < cut)) {
            cut = t;
        }
        return cut > 0 ? raw.substring(0, cut) : raw;
    }

    private static String cell(List<String> cols, Map<String, Integer> idx, String... aliases) {
        for (String a : aliases) {
            Integer i = idx.get(normHeader(a));
            if (i != null && i >= 0 && i < cols.size()) {
                String v = cols.get(i);
                if (v != null) {
                    String t = v.trim();
                    if (!t.isEmpty()) {
                        return t;
                    }
                }
            }
        }
        return "";
    }

    private static BigDecimal decimalCell(
            List<String> cols,
            Map<String, Integer> idx,
            List<String> errors,
            int lineNo,
            boolean quantityLike,
            String... aliases) {
        String raw = cell(cols, idx, aliases);
        if (raw.isEmpty()) {
            return null;
        }
        String cleaned = raw.replace("$", "").replace(",", "").replace("(", "-").replace(")", "").trim();
        if (quantityLike) {
            // Robinhood exports occasionally append a trade-side suffix (e.g., "2S", "1B") in quantity cells.
            cleaned = cleaned.replaceAll("(?i)([+-]?\\d+(?:\\.\\d+)?)\\s*[A-Z]+$", "$1");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            errors.add("Line " + lineNo + ": invalid numeric value '" + raw + "'");
            return null;
        }
    }

    private static Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            idx.put(normHeader(headers.get(i)), i);
        }
        return idx;
    }

    private static String normHeader(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return s.replace('_', ' ').replaceAll("\\s+", " ");
    }

    /**
     * Splits CSV into records while respecting quoted multiline fields.
     */
    private static List<String> splitCsvRecords(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> out = new ArrayList<>();
        StringBuilder rec = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < normalized.length() && normalized.charAt(i + 1) == '"') {
                    rec.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                rec.append(c);
                continue;
            }
            if (c == '\n' && !quoted) {
                out.add(rec.toString());
                rec.setLength(0);
                continue;
            }
            rec.append(c);
        }
        if (rec.length() > 0) {
            out.add(rec.toString());
        }
        return out;
    }

    /** Minimal CSV parser supporting quoted fields and escaped quotes. */
    private static List<String> parseCsvLine(String line) {
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

    private String qualifiedTable() {
        String tbl = props.robinhoodTable().trim();
        String[] parts = tbl.split("\\.");
        if (parts.length == 1) {
            return column(parts[0]);
        }
        if (parts.length == 2) {
            return column(parts[0]) + "." + column(parts[1]);
        }
        throw new IllegalArgumentException("tracker.finance.robinhood-table must be TABLE or SCHEMA.TABLE");
    }

    private static String column(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (!SAFE_IDENT.matcher(s).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + raw);
        }
        return s;
    }

    private static boolean blank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private record ParsedRow(
            int lineNo,
            Timestamp activityDate,
            Timestamp processDate,
            Timestamp settleDate,
            String instrument,
            String description,
            String transCode,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal amount) {
        Map<String, String> asMap() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("ACTIVITY_DATE", toTs(activityDate));
            m.put("PROCESS_DATE", toTs(processDate));
            m.put("SETTLE_DATE", toTs(settleDate));
            m.put("INSTRUMENT", toStr(instrument));
            m.put("DESCRIPTION", toStr(description));
            m.put("TRANS_CODE", toStr(transCode));
            m.put("QUANTITY", quantity == null ? "" : quantity.toPlainString());
            m.put("PRICE", price == null ? "" : price.toPlainString());
            m.put("AMOUNT", amount == null ? "" : amount.toPlainString());
            return m;
        }

        private static String toStr(String s) {
            return s == null ? "" : s;
        }

        private static String toTs(Timestamp ts) {
            return ts == null ? "" : ts.toInstant().toString();
        }
    }
}
