package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.RobinhoodStocksSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodStocksSummaryRow;
import com.svp.tracker.finance.dto.RobinhoodTransactionsDto;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodFinanceService {

    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#]{0,127}$");
    /** Bound values use {@code ?}; length bound only (tickers, instrument URLs, etc.). */
    private static final int MAX_STOCK_FILTER_CHARS = 4000;
    private static final String T = "t";
    private static final int RESULT_PREVIEW_CHARS = 4000;

    private final JdbcTemplate jdbcTemplate;
    private final FinanceProperties props;
    private final CurrentUserService currentUser;

    /**
     * @param year optional; with {@code month} filters to that month, alone filters to calendar year
     * @param month optional 1–12; requires {@code year}
     * @param symbolFilter optional value matching the configured stock column (e.g. ticker or instrument URL);
     *     filters {@code UPPER(TRIM(column)) = UPPER(?)} when set
     */
    public RobinhoodTransactionsDto fetchTransactions(Integer year, Integer month, String symbolFilter) {
        String symbol = sanitizeSymbolFilter(symbolFilter);
        if (symbol != null && qualifiedStockSymbolColumn() == null) {
            throw new IllegalStateException("Configure tracker.finance.stock-symbol-column to filter by symbol");
        }

        int cap = props.maxTransactionRows();
        try {
            List<Map<String, Object>> rows = queryTransactionRows(year, month, symbol, cap);
            String label = filterLabel(year, month, symbol);
            RobinhoodTransactionsDto dto =
                    new RobinhoodTransactionsDto(
                            rows, rows.size(), props.robinhoodTable(), cap, year, month, label);

            log.info(
                    "Robinhood query succeeded: {} row(s) from {} | {}",
                    rows.size(),
                    props.robinhoodTable(),
                    label);
            if (rows.isEmpty()) {
                log.info("Robinhood result: no rows for this filter");
            } else {
                String preview = truncateForLog(rows.get(0).toString(), RESULT_PREVIEW_CHARS);
                log.info("Robinhood result preview (first row): {}", preview);
            }
            return dto;
        } catch (DataAccessException e) {
            log.error("Robinhood query failed | message: {}", e.getMessage(), e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Robinhood query failed | message: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Buy/sell summary by instrument and contract (description) for a calendar year. Uses Robinhood-style trans codes:
     * {@code BTO}/{@code Buy} as buys; {@code STC}/{@code Sell} as sells. Other codes (e.g. ACH) are skipped.
     */
    public RobinhoodStocksSummaryDto fetchStocksSummary(int financialYear, String symbolFilter) {
        String symbol = sanitizeSymbolFilter(symbolFilter);
        if (symbol != null && qualifiedStockSymbolColumn() == null) {
            throw new IllegalStateException("Configure tracker.finance.stock-symbol-column to filter by symbol");
        }
        if (props.transactionDateColumn().isBlank()) {
            throw new IllegalStateException("Configure tracker.finance.transaction-date-column for stocks summary");
        }
        int cap = props.maxStocksSummaryRows();
        List<Map<String, Object>> rows = queryTransactionRows(financialYear, null, symbol, cap);
        boolean truncated = rows.size() >= cap;
        List<RobinhoodStocksSummaryRow> summaryRows = buildStocksSummaryRows(rows, financialYear);
        summaryRows.sort(
                Comparator.comparing(RobinhoodStocksSummaryRow::instrument, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(RobinhoodStocksSummaryRow::contract, String.CASE_INSENSITIVE_ORDER));
        String note =
                truncated
                        ? "Summary used the first "
                                + cap
                                + " transaction rows for this year filter; totals may be incomplete if more rows exist."
                        : "";
        return new RobinhoodStocksSummaryDto(
                summaryRows,
                financialYear,
                symbol,
                props.robinhoodTable(),
                cap,
                truncated,
                note);
    }

    private List<Map<String, Object>> queryTransactionRows(Integer year, Integer month, String symbol, int cap) {
        String table = qualifiedTable();
        String qualifiedDateCol = qualifiedTransactionDateColumn();
        String dateExpr = activityDateExpression(qualifiedDateCol);
        String qualSym = qualifiedStockSymbolColumn();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(T).append(".* FROM ").append(table).append(" ").append(T);

        List<Object> prefixBinds = new ArrayList<>();
        boolean hasWhere = appendUserOwnerClause(sql, prefixBinds);

        boolean hasDate = year != null;
        boolean hasSym = symbol != null;
        if (hasDate) {
            if (dateExpr == null) {
                throw new IllegalStateException("Date column not configured");
            }
            LocalDate rangeStart = filterStartInclusive(year, month);
            LocalDate rangeEndEx = filterEndExclusive(year, month);
            String[] oracleStringBounds = oracleStringBoundsForFilter(rangeStart, rangeEndEx);
            String maskTrim =
                    props.transactionDateOracleFormatMask() == null
                            ? ""
                            : props.transactionDateOracleFormatMask().trim();
            boolean isoDayPrefixBounds =
                    oracleStringBounds != null && maskTrim.equalsIgnoreCase("YYYY-MM-DD");
            sql.append(hasWhere ? " AND " : " WHERE ");
            hasWhere = true;
            if (oracleStringBounds != null && !isoDayPrefixBounds) {
                String quotedMask = maskTrim.replace("'", "''");
                sql.append(dateExpr)
                        .append(" >= to_timestamp(?, '")
                        .append(quotedMask)
                        .append("') AND ")
                        .append(dateExpr)
                        .append(" < to_timestamp(?, '")
                        .append(quotedMask)
                        .append("')");
            } else {
                sql.append(dateExpr).append(" >= ? AND ").append(dateExpr).append(" < ?");
            }
            if (hasSym) {
                sql.append(" AND ").append(symbolEqualityPredicate(qualSym));
            }
        } else if (hasSym) {
            sql.append(hasWhere ? " AND " : " WHERE ");
            hasWhere = true;
            sql.append(symbolEqualityPredicate(qualSym));
        }

        if (dateExpr != null) {
            sql.append(" ORDER BY ").append(dateExpr).append(" ASC NULLS LAST");
        }
        sql.append(" LIMIT ?");

        String sqlForLog = expandBindsForLog(sql.toString(), year, month, symbol, cap);
        log.debug("Robinhood query: {}", sqlForLog);

        return jdbcTemplate.query(
                sql.toString(),
                ps -> {
                    int i = 1;
                    for (Object o : prefixBinds) {
                        ps.setObject(i++, o);
                    }
                    if (year != null) {
                        LocalDate start = filterStartInclusive(year, month);
                        LocalDate endExclusive = filterEndExclusive(year, month);
                        String[] ob = oracleStringBoundsForFilter(start, endExclusive);
                        if (ob != null) {
                            ps.setString(i++, ob[0]);
                            ps.setString(i++, ob[1]);
                        } else {
                            ps.setTimestamp(i++, Timestamp.valueOf(start.atStartOfDay()));
                            ps.setTimestamp(i++, Timestamp.valueOf(endExclusive.atStartOfDay()));
                        }
                    }
                    if (symbol != null) {
                        ps.setString(i++, symbol);
                    }
                    ps.setInt(i, cap);
                },
                new ColumnMapRowMapper());
    }

    /** Restrict Robinhood SQL to rows owned by the signed-in user ({@code owner_user_id}). */
    private boolean appendUserOwnerClause(StringBuilder sql, List<Object> prefixBinds) {
        sql.append(" WHERE ").append(T).append(".owner_user_id = ?");
        prefixBinds.add(currentUser.requireUserId());
        return true;
    }

    private List<RobinhoodStocksSummaryRow> buildStocksSummaryRows(List<Map<String, Object>> rows, int financialYear) {
        Map<String, SummaryAgg> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String trans = stringCell(row, "TRANS_CODE");
            Leg leg = classifyLeg(trans);
            if (leg == Leg.OTHER) {
                continue;
            }
            String inst = Objects.requireNonNullElse(stringCell(row, "INSTRUMENT"), "").trim();
            if (inst.isEmpty()) {
                inst = "—";
            }
            String contract = Objects.requireNonNullElse(stringCell(row, "DESCRIPTION"), "").trim();
            if (contract.isEmpty()) {
                contract = "—";
            }
            final String instKey = inst;
            final String contractKey = contract;
            BigDecimal qty = decimalCell(row, "QUANTITY");
            BigDecimal amt = decimalCell(row, "AMOUNT");
            LocalDate activity = localDateCell(row, "ACTIVITY_DATE");
            String key = instKey + "\u0001" + contractKey;
            SummaryAgg agg = byKey.computeIfAbsent(key, k -> new SummaryAgg(instKey, contractKey, financialYear));
            if (leg == Leg.BUY) {
                agg.addBuy(activity, qty, amt);
            } else {
                agg.addSell(activity, qty, amt);
            }
        }
        List<RobinhoodStocksSummaryRow> out = new ArrayList<>(byKey.size());
        for (SummaryAgg a : byKey.values()) {
            out.add(a.toRow());
        }
        return out;
    }

    private enum Leg {
        BUY,
        SELL,
        OTHER
    }

    private static Leg classifyLeg(String transCode) {
        if (transCode == null) {
            return Leg.OTHER;
        }
        String u = transCode.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "BTO", "BTC", "BUY" -> Leg.BUY;
            case "STC", "STO", "SELL" -> Leg.SELL;
            default -> Leg.OTHER;
        };
    }

    private static final class SummaryAgg {
        final String instrument;
        final String contract;
        final int financialYear;
        BigDecimal buyQty = BigDecimal.ZERO;
        BigDecimal sellQty = BigDecimal.ZERO;
        BigDecimal buyAmt = BigDecimal.ZERO;
        BigDecimal sellAmt = BigDecimal.ZERO;
        int buyLegs;
        int sellLegs;
        LocalDate firstBuy;
        LocalDate lastBuy;
        LocalDate firstSell;
        LocalDate lastSell;

        SummaryAgg(String instrument, String contract, int financialYear) {
            this.instrument = instrument;
            this.contract = contract;
            this.financialYear = financialYear;
        }

        void addBuy(LocalDate d, BigDecimal qty, BigDecimal amt) {
            buyLegs++;
            if (qty != null) {
                buyQty = buyQty.add(qty.abs());
            }
            if (amt != null) {
                buyAmt = buyAmt.add(amt);
            }
            touch(d, true);
        }

        void addSell(LocalDate d, BigDecimal qty, BigDecimal amt) {
            sellLegs++;
            if (qty != null) {
                sellQty = sellQty.add(qty.abs());
            }
            if (amt != null) {
                sellAmt = sellAmt.add(amt);
            }
            touch(d, false);
        }

        private void touch(LocalDate d, boolean buy) {
            if (d == null) {
                return;
            }
            if (buy) {
                if (firstBuy == null || d.isBefore(firstBuy)) {
                    firstBuy = d;
                }
                if (lastBuy == null || d.isAfter(lastBuy)) {
                    lastBuy = d;
                }
            } else {
                if (firstSell == null || d.isBefore(firstSell)) {
                    firstSell = d;
                }
                if (lastSell == null || d.isAfter(lastSell)) {
                    lastSell = d;
                }
            }
        }

        RobinhoodStocksSummaryRow toRow() {
            BigDecimal net = buyAmt.add(sellAmt);
            return new RobinhoodStocksSummaryRow(
                    instrument,
                    contract,
                    financialYear,
                    buyQty,
                    sellQty,
                    buyAmt,
                    sellAmt,
                    net,
                    firstBuy,
                    lastBuy,
                    firstSell,
                    lastSell,
                    buyLegs,
                    sellLegs);
        }
    }

    private static Object rawCell(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String stringCell(Map<String, Object> row, String name) {
        Object v = rawCell(row, name);
        return v == null ? null : v.toString();
    }

    private static BigDecimal decimalCell(Map<String, Object> row, String name) {
        Object v = rawCell(row, name);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate localDateCell(Map<String, Object> row, String name) {
        Object v = rawCell(row, name);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate ld) {
            return ld;
        }
        if (v instanceof Timestamp ts) {
            return ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (v instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (v instanceof java.sql.Date sd) {
            return sd.toLocalDate();
        }
        if (v instanceof String s) {
            String t = s.trim();
            if (t.length() >= 10) {
                return LocalDate.parse(t.substring(0, 10));
            }
        }
        return null;
    }

    private static String filterLabel(Integer year, Integer month, String symbol) {
        String base;
        if (year == null) {
            base = "All rows (within cap)";
        } else if (month == null || month < 1 || month > 12) {
            base = "Year " + year;
        } else {
            String m = Month.of(month).getDisplayName(TextStyle.FULL, Locale.US);
            base = m + " " + year;
        }
        if (symbol != null && !symbol.isEmpty()) {
            String shown =
                    symbol.length() > 120 ? symbol.substring(0, 117) + "…" : symbol;
            return base + " · " + shown;
        }
        return base;
    }

    /**
     * Distinct values from the configured stock column ({@code TRIM}), ordered, capped. Uses plain {@code TRIM} for
     * {@code VARCHAR2}/{@code CHAR} tickers (typical Robinhood exports). LOB/NCLOB tables can set
     * {@link FinanceProperties#stockSymbolColumnOracleExpr()} to override the SQL expression (e.g. {@code
     * TO_CHAR(instrument)}).
     */
    public List<String> fetchDistinctStockSymbols() {
        String qual = qualifiedStockSymbolColumn();
        if (qual == null) {
            throw new IllegalStateException("Configure tracker.finance.stock-symbol-column for symbol list");
        }
        String table = qualifiedTable();
        int cap = props.maxSymbolListRows();
        String trimExpr = stockColumnTrimExpression(qual);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT ")
                .append(trimExpr)
                .append(" AS SYM FROM ")
                .append(table)
                .append(" ")
                .append(T)
                .append(" WHERE ")
                .append(qual)
                .append(" IS NOT NULL AND ")
                .append(trimExpr)
                .append(" IS NOT NULL");
        List<Object> symBinds = new ArrayList<>();
        sql.append(" AND ").append(T).append(".owner_user_id = ?");
        symBinds.add(currentUser.requireUserId());
        sql.append(" ORDER BY 1 LIMIT ?");
        log.debug("Robinhood symbols query: {}", sql.toString().replaceFirst("\\?", Integer.toString(cap)));
        try {
            List<Map<String, Object>> rows =
                    jdbcTemplate.query(
                            sql.toString(),
                            ps -> {
                                int i = 1;
                                for (Object o : symBinds) {
                                    ps.setObject(i++, o);
                                }
                                ps.setInt(i, cap);
                            },
                            new ColumnMapRowMapper());
            List<String> out = materializeSymbolRows(rows);
            if (out.isEmpty() && !rows.isEmpty()) {
                log.warn(
                        "Robinhood symbols: DISTINCT returned {} JDBC row(s) but none parsed; first keys={}",
                        rows.size(),
                        rows.get(0).keySet());
            }
            if (out.isEmpty()) {
                out = fetchDistinctStockSymbolsFallback(qual, table, trimExpr, cap);
            }
            log.info("Robinhood symbols list: {} distinct value(s)", out.size());
            return Collections.unmodifiableList(out);
        } catch (DataAccessException e) {
            log.error("Robinhood symbols query failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<String> materializeSymbolRows(List<Map<String, Object>> rows) {
        List<String> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object v = firstColumnValue(row);
            String s = jdbcValueToString(v);
            if (s != null && !s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * If the primary DISTINCT query returns no usable strings (driver/Oracle quirks), scan non-null values and
     * distinct in memory (capped).
     */
    private List<String> fetchDistinctStockSymbolsFallback(
            String qual, String table, String trimExpr, int cap) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
                .append(trimExpr)
                .append(" AS SYM FROM ")
                .append(table)
                .append(" ")
                .append(T)
                .append(" WHERE ")
                .append(qual)
                .append(" IS NOT NULL");
        List<Object> fbBinds = new ArrayList<>();
        sql.append(" AND ").append(T).append(".owner_user_id = ?");
        fbBinds.add(currentUser.requireUserId());
        sql.append(" LIMIT ?");
        log.info("Robinhood symbols: primary DISTINCT yielded no values; trying fallback scan (cap {})", cap);
        try {
            List<Map<String, Object>> rows =
                    jdbcTemplate.query(
                            sql.toString(),
                            ps -> {
                                int i = 1;
                                for (Object o : fbBinds) {
                                    ps.setObject(i++, o);
                                }
                                ps.setInt(i, Math.min(cap * 20, 100_000));
                            },
                            new ColumnMapRowMapper());
            java.util.TreeSet<String> set = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Map<String, Object> row : rows) {
                String s = jdbcValueToString(firstColumnValue(row));
                if (s != null && !s.isEmpty()) {
                    set.add(s);
                    if (set.size() >= cap) {
                        break;
                    }
                }
            }
            return new ArrayList<>(set);
        } catch (DataAccessException e) {
            log.warn("Robinhood symbols fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** {@code TRIM(col)} or configured Oracle expression (trimmed). */
    private String stockColumnTrimExpression(String qualifiedCol) {
        String raw = props.stockSymbolColumnOracleExpr();
        if (raw != null && !raw.trim().isEmpty()) {
            return raw.trim();
        }
        return "TRIM(" + qualifiedCol + ")";
    }

    /** Single-column result: avoid relying on alias key casing from Oracle/JDBC. */
    private static Object firstColumnValue(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object v = row.get("SYM");
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase("SYM")) {
                return e.getValue();
            }
        }
        return row.values().iterator().next();
    }

    private static String jdbcValueToString(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s.trim();
        }
        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Number n) {
            return n.toString();
        }
        if (v instanceof Clob clob) {
            try {
                long len = clob.length();
                if (len <= 0) {
                    return "";
                }
                int n = (int) Math.min(len, 4000);
                return clob.getSubString(1, n).trim();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not read CLOB for instrument column", e);
            }
        }
        return v.toString().trim();
    }

    private static String sanitizeSymbolFilter(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > MAX_STOCK_FILTER_CHARS) {
            throw new IllegalArgumentException("Invalid symbol filter");
        }
        if (t.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Invalid symbol filter");
        }
        return t;
    }

    /**
     * Exact match after trim; expression matches {@link #stockColumnTrimExpression(String)} (default {@code TRIM}).
     */
    private String symbolEqualityPredicate(String qualifiedSymbolCol) {
        return stockColumnTrimExpression(qualifiedSymbolCol) + " = ?";
    }

    private String expandBindsForLog(String sql, Integer year, Integer month, String symbol, int cap) {
        String s = sql;
        if (year != null) {
            LocalDate start = filterStartInclusive(year, month);
            LocalDate endExclusive = filterEndExclusive(year, month);
            String[] ob = oracleStringBoundsForFilter(start, endExclusive);
            if (ob != null) {
                s = s.replaceFirst("\\?", "'" + ob[0].replace("'", "''") + "'");
                s = s.replaceFirst("\\?", "'" + ob[1].replace("'", "''") + "'");
            } else {
                s = s.replaceFirst("\\?", "'" + start + "T00:00:00'");
                s = s.replaceFirst("\\?", "'" + endExclusive + "T00:00:00'");
            }
        }
        if (symbol != null) {
            s = s.replaceFirst("\\?", "'" + symbol.replace("'", "''") + "'");
        }
        return s.replaceFirst("\\?", Integer.toString(cap));
    }

    /**
     * When {@link FinanceProperties#transactionDateOracleFormatMask()} is set and we can format bound days in that
     * mask, returns {@code [startInclusive, endExclusive)} as strings for {@code TO_TIMESTAMP(?, mask)}. Otherwise
     * {@code null} (caller uses JDBC {@link Timestamp} against a native date/time column).
     */
    private String[] oracleStringBoundsForFilter(LocalDate startInclusive, LocalDate endExclusive) {
        String raw = props.transactionDateOracleFormatMask();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String m = raw.trim();
        String a = formatLocalDateForOracleMask(startInclusive, m);
        String b = formatLocalDateForOracleMask(endExclusive, m);
        if (a == null || b == null) {
            return null;
        }
        return new String[] {a, b};
    }

    /**
     * Formats a calendar day as Oracle expects for {@code TO_TIMESTAMP(?, mask)}. Extend as needed for other masks.
     */
    private static String formatLocalDateForOracleMask(LocalDate d, String oracleMask) {
        if (oracleMask.equalsIgnoreCase("YYYY-MM-DD")) {
            return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (oracleMask.equalsIgnoreCase("MM/DD/YYYY")) {
            return d.format(DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US));
        }
        if (oracleMask.equalsIgnoreCase("DD/MM/YYYY")) {
            return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US));
        }
        if (oracleMask.equalsIgnoreCase("DD-MON-YYYY")) {
            return d.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US)).toUpperCase(Locale.US);
        }
        return null;
    }

    private static LocalDate filterStartInclusive(Integer year, Integer month) {
        return LocalDate.of(year, month != null ? month : 1, 1);
    }

    private static LocalDate filterEndExclusive(Integer year, Integer month) {
        LocalDate start = filterStartInclusive(year, month);
        return month != null ? start.plusMonths(1) : start.plusYears(1);
    }

    private static String truncateForLog(String str, int max) {
        if (str == null) {
            return "";
        }
        if (str.length() <= max) {
            return str;
        }
        return str.substring(0, max) + "…";
    }

    /**
     * Column fragment for SQL (either unquoted {@code ACTIVITY_DATE} or quoted {@code "activity_date"}), or null if
     * not configured.
     */
    /** {@code t."col"} or {@code t.SYMBOL}, or null if not configured. */
    private String qualifiedStockSymbolColumn() {
        String id = symbolColumnSqlIdent();
        if (id == null) {
            return null;
        }
        return T + "." + id;
    }

    private String symbolColumnSqlIdent() {
        if (props.stockSymbolColumn().isBlank()) {
            return null;
        }
        if (props.stockSymbolColumnQuoted()) {
            return quotedOracleIdent(props.stockSymbolColumn());
        }
        return column(props.stockSymbolColumn());
    }

    /** {@code t."col"} or {@code t.ACTIVITY_DATE}, or null if not configured. */
    private String qualifiedTransactionDateColumn() {
        String dateIdent = transactionDateSqlIdent();
        if (dateIdent == null) {
            return null;
        }
        return T + "." + dateIdent;
    }

    /**
     * Expression for range filter and {@code ORDER BY}:
     * <ul>
     *   <li>No mask: native {@code DATE}/{@code TIMESTAMP} column (use JDBC timestamp binds).
     *   <li>{@code YYYY-MM-DD}: leading calendar day of a string (or string form of a date), lexicographic order matches
     *       chronological order for zero-padded ISO dates.
     *   <li>Other masks: {@code TO_TIMESTAMP(TRIM(col), mask)} for VARCHAR2/CHAR stored in that format.
     * </ul>
     */
    private String activityDateExpression(String qualifiedColumn) {
        if (qualifiedColumn == null) {
            return null;
        }
        String mask = props.transactionDateOracleFormatMask();
        if (mask == null || mask.isBlank()) {
            return qualifiedColumn;
        }
        String m = mask.trim();
        if (m.equalsIgnoreCase("YYYY-MM-DD")) {
            return "SUBSTR(TRIM(" + qualifiedColumn + "::text), 1, 10)";
        }
        return "to_timestamp(TRIM(" + qualifiedColumn + "::text), '" + m.replace("'", "''") + "')";
    }

    private String transactionDateSqlIdent() {
        if (props.transactionDateColumn().isBlank()) {
            return null;
        }
        if (props.transactionDateQuoted()) {
            return quotedOracleIdent(props.transactionDateColumn());
        }
        return column(props.transactionDateColumn());
    }

    /** Oracle quoted identifier: preserves case. Inner name must match {@link #SAFE_IDENT}. */
    private static String quotedOracleIdent(String raw) {
        String inner = raw.trim();
        if (inner.startsWith("\"") && inner.endsWith("\"") && inner.length() >= 2) {
            inner = inner.substring(1, inner.length() - 1).replace("\"\"", "\"");
        }
        if (!SAFE_IDENT.matcher(inner).matches()) {
            throw new IllegalArgumentException("Invalid Oracle quoted identifier (inner part): " + raw);
        }
        return "\"" + inner + "\"";
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
}
