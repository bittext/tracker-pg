package com.svp.tracker.admin.service;

import com.svp.tracker.admin.dto.usage.DailyActivityPointDto;
import com.svp.tracker.admin.dto.usage.FeatureUsageDto;
import com.svp.tracker.admin.dto.usage.MemberUsageDto;
import com.svp.tracker.admin.dto.usage.SignInDailyPointDto;
import com.svp.tracker.admin.dto.usage.UsageSummaryDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregation queries for the Admin → Usage tab.
 *
 * <p>All SQL here is intentionally hand-written against the Postgres schema (not JPA), because the queries span
 * many tables with UNIONs / correlated sub-selects and don't fit well as derived repository methods. Each method is
 * read-only and ADMIN-gated upstream via {@code /api/admin/**}.
 */
@Service
@RequiredArgsConstructor
public class AdminUsageService {

    private final JdbcTemplate jdbc;

    /**
     * Per-feature mapping: display label → SQL fragment {@code FROM <table>} (must have {@code owner_user_id} and
     * {@code created_at}). Order is preserved in API responses.
     */
    private static final Map<String, FeatureSource> FEATURES = orderedMap(
            entry("Tasks", new FeatureSource("management_tasks", "owner_user_id", "created_at")),
            entry("Month notes", new FeatureSource("management_month_notes", "owner_user_id", "created_at")),
            entry("Write-ups", new FeatureSource("management_writeups", "owner_user_id", "created_at")),
            entry("Work log", new FeatureSource("management_work_log_entries", "owner_user_id", "created_at")),
            entry("Travel", new FeatureSource("management_travel_trips", "owner_user_id", "created_at")),
            entry("Documents", new FeatureSource("management_documents", "owner_user_id", "created_at")),
            entry("Accounts", new FeatureSource("management_accounts", "owner_user_id", "created_at")),
            entry("Banking", new FeatureSource("banking_institutions", "owner_user_id", "created_at")),
            entry("Transactions", new FeatureSource("banking_transactions", "owner_user_id", "created_at")),
            entry("Imports", new FeatureSource("banking_import_files", "owner_user_id", "created_at")),
            entry("Journal", new FeatureSource("journal_entries", "owner_user_id", "created_at")),
            entry("Calendar", new FeatureSource("report_calendar_entries", "owner_user_id", "created_at")),
            entry("Stock alerts", new FeatureSource("finance_stock_alerts", "owner_user_id", "created_at")));

    @Transactional(readOnly = true)
    public UsageSummaryDto summary() {
        long totalUsers = countOrZero("SELECT COUNT(*) FROM auth_users");
        long activeUsers = countOrZero("SELECT COUNT(*) FROM auth_users WHERE active = TRUE");
        long admins = countOrZero("SELECT COUNT(*) FROM auth_users WHERE role = 'ADMIN'");
        long memberProfiles = countOrZero("SELECT COUNT(*) FROM auth_users WHERE member_public_id IS NOT NULL");

        long active7 = countOrZero(
                "SELECT COUNT(DISTINCT user_id) FROM auth_login_events "
                        + "WHERE event_type = 'LOGIN_SUCCESS' AND user_id IS NOT NULL "
                        + "AND created_at >= NOW() - INTERVAL '7 days'");
        long active30 = countOrZero(
                "SELECT COUNT(DISTINCT user_id) FROM auth_login_events "
                        + "WHERE event_type = 'LOGIN_SUCCESS' AND user_id IS NOT NULL "
                        + "AND created_at >= NOW() - INTERVAL '30 days'");
        long signInsOk30 = countOrZero(
                "SELECT COUNT(*) FROM auth_login_events "
                        + "WHERE event_type = 'LOGIN_SUCCESS' AND created_at >= NOW() - INTERVAL '30 days'");
        long signInsFail30 = countOrZero(
                "SELECT COUNT(*) FROM auth_login_events "
                        + "WHERE event_type IN ('LOGIN_FAILED','MFA_FAILED') "
                        + "AND created_at >= NOW() - INTERVAL '30 days'");

        String unionAll7 = unionAll(7);
        String unionAll30 = unionAll(30);
        long items7 = unionAll7.isEmpty() ? 0L : countOrZero("SELECT COUNT(*) FROM (" + unionAll7 + ") t");
        long items30 = unionAll30.isEmpty() ? 0L : countOrZero("SELECT COUNT(*) FROM (" + unionAll30 + ") t");

        String lastActivity = maxOfMaxes();

        return new UsageSummaryDto(
                totalUsers,
                activeUsers,
                admins,
                memberProfiles,
                active7,
                active30,
                signInsOk30,
                signInsFail30,
                items7,
                items30,
                lastActivity);
    }

    @Transactional(readOnly = true)
    public List<FeatureUsageDto> featureUsage(int days) {
        int d = clampDays(days);
        List<FeatureUsageDto> out = new ArrayList<>();
        for (Map.Entry<String, FeatureSource> e : FEATURES.entrySet()) {
            FeatureSource s = e.getValue();
            String sqlWindow = "SELECT COUNT(*), COUNT(DISTINCT " + s.userCol() + ") FROM " + s.table()
                    + " WHERE " + s.tsCol() + " >= NOW() - INTERVAL '" + d + " days'";
            long[] window = jdbc.queryForObject(sqlWindow, (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)});
            long total = window == null ? 0 : window[0];
            long users = window == null ? 0 : window[1];
            long allTime = countOrZero("SELECT COUNT(*) FROM " + s.table());
            String lastAt = maxOf(s.table(), s.tsCol());
            out.add(new FeatureUsageDto(e.getKey(), total, users, allTime, lastAt));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<DailyActivityPointDto> dailyActivity(int days) {
        int d = clampDays(days);
        String unionAll = unionAllNamed(d);
        if (unionAll.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT (date_trunc('day', ts AT TIME ZONE 'UTC'))::date::text AS day, "
                + "       feature, COUNT(*) AS cnt "
                + "FROM (" + unionAll + ") a "
                + "GROUP BY 1, 2 "
                + "ORDER BY 1, 2";
        return jdbc.query(sql, (rs, i) -> new DailyActivityPointDto(
                rs.getString("day"), rs.getString("feature"), rs.getLong("cnt")));
    }

    @Transactional(readOnly = true)
    public List<SignInDailyPointDto> signInDaily(int days) {
        int d = clampDays(days);
        String sql = "SELECT (date_trunc('day', created_at AT TIME ZONE 'UTC'))::date::text AS day, "
                + "       event_type, COUNT(*) AS cnt "
                + "FROM auth_login_events "
                + "WHERE created_at >= NOW() - INTERVAL '" + d + " days' "
                + "GROUP BY 1, 2 "
                + "ORDER BY 1, 2";
        return jdbc.query(sql, (rs, i) -> new SignInDailyPointDto(
                rs.getString("day"), rs.getString("event_type"), rs.getLong("cnt")));
    }

    @Transactional(readOnly = true)
    public List<MemberUsageDto> members(int days) {
        int d = clampDays(days);
        StringBuilder counts = new StringBuilder();
        for (Map.Entry<String, FeatureSource> e : FEATURES.entrySet()) {
            FeatureSource s = e.getValue();
            counts.append(", (SELECT COUNT(*) FROM ")
                    .append(s.table())
                    .append(" WHERE ")
                    .append(s.userCol())
                    .append(" = u.id) AS ")
                    .append(safeAlias(e.getKey()));
        }
        String sql = "SELECT u.id, u.username, u.role, u.active, u.created_at, "
                + "       p.first_name, p.last_name, p.nickname, "
                + "       (SELECT MAX(created_at) FROM auth_login_events e WHERE e.user_id = u.id AND e.event_type = 'LOGIN_SUCCESS') AS last_login_at, "
                + "       (SELECT COUNT(*) FROM auth_login_events e WHERE e.user_id = u.id AND e.event_type = 'LOGIN_SUCCESS' AND e.created_at >= NOW() - INTERVAL '"
                + d + " days') AS sign_ins_success "
                + counts
                + " FROM auth_users u "
                + " LEFT JOIN member_profiles p ON p.user_id = u.id "
                + " ORDER BY u.id ASC";
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Long> per = new LinkedHashMap<>();
            long total = 0;
            for (String label : FEATURES.keySet()) {
                long c = rs.getLong(safeAlias(label));
                per.put(label, c);
                total += c;
            }
            String first = nullToBlank(rs.getString("first_name"));
            String last = nullToBlank(rs.getString("last_name"));
            String nick = nullToBlank(rs.getString("nickname"));
            String display = (first + " " + last).trim();
            if (display.isEmpty() && !nick.isEmpty()) {
                display = nick;
            }
            return new MemberUsageDto(
                    rs.getLong("id"),
                    rs.getString("username"),
                    display,
                    rs.getString("role"),
                    rs.getBoolean("active"),
                    instantToString(rs.getTimestamp("created_at")),
                    instantToString(rs.getTimestamp("last_login_at")),
                    rs.getLong("sign_ins_success"),
                    total,
                    per);
        });
    }

    private long countOrZero(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }

    /** {@code SELECT 1 FROM <t1> WHERE ts >= ... UNION ALL ...} — used inside {@code SELECT COUNT(*) FROM (...) t}. */
    private static String unionAll(int days) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (FeatureSource s : FEATURES.values()) {
            if (!first) {
                sb.append(" UNION ALL ");
            }
            sb.append("SELECT 1 FROM ")
                    .append(s.table())
                    .append(" WHERE ")
                    .append(s.tsCol())
                    .append(" >= NOW() - INTERVAL '")
                    .append(days)
                    .append(" days'");
            first = false;
        }
        return sb.toString();
    }

    /** Same shape as {@link #unionAll(int)} but each branch projects {@code feature, ts} for day-bucketing. */
    private static String unionAllNamed(int days) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, FeatureSource> e : FEATURES.entrySet()) {
            FeatureSource s = e.getValue();
            if (!first) {
                sb.append(" UNION ALL ");
            }
            sb.append("SELECT '")
                    .append(e.getKey().replace("'", "''"))
                    .append("' AS feature, ")
                    .append(s.tsCol())
                    .append(" AS ts FROM ")
                    .append(s.table())
                    .append(" WHERE ")
                    .append(s.tsCol())
                    .append(" >= NOW() - INTERVAL '")
                    .append(days)
                    .append(" days'");
            first = false;
        }
        return sb.toString();
    }

    private String maxOf(String table, String tsCol) {
        Timestamp t = jdbc.queryForObject("SELECT MAX(" + tsCol + ") FROM " + table, Timestamp.class);
        return instantToString(t);
    }

    private String maxOfMaxes() {
        Instant best = null;
        for (FeatureSource s : FEATURES.values()) {
            Timestamp t = jdbc.queryForObject("SELECT MAX(" + s.tsCol() + ") FROM " + s.table(), Timestamp.class);
            if (t != null) {
                Instant ti = t.toInstant();
                if (best == null || ti.isAfter(best)) {
                    best = ti;
                }
            }
        }
        return best == null ? null : best.toString();
    }

    private static int clampDays(int days) {
        if (days <= 0) {
            return 30;
        }
        return Math.min(days, 365);
    }

    private static String instantToString(Timestamp t) {
        return t == null ? null : t.toInstant().toString();
    }

    private static String nullToBlank(String s) {
        return s == null ? "" : s;
    }

    /** Turn a feature label like "Month notes" into a valid SQL column alias. */
    private static String safeAlias(String label) {
        StringBuilder sb = new StringBuilder("f_");
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private record FeatureSource(String table, String userCol, String tsCol) {}

    @SafeVarargs
    private static <K, V> Map<K, V> orderedMap(Map.Entry<K, V>... entries) {
        Map<K, V> m = new LinkedHashMap<>();
        for (Map.Entry<K, V> e : entries) {
            m.put(e.getKey(), e.getValue());
        }
        return m;
    }

    private static <K, V> Map.Entry<K, V> entry(K k, V v) {
        return Map.entry(k, v);
    }
}
