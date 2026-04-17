package com.svp.tracker.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

/**
 * One-shot copy of Tracker business tables from Oracle (sibling app schema) into PostgreSQL. Does not touch {@code
 * auth_users}.
 *
 * <p>Required environment variables:
 *
 * <ul>
 *   <li>{@code ORACLE_JDBC_URL} — e.g. {@code jdbc:oracle:thin:@//localhost:1521/FREEPDB1}
 *   <li>{@code ORACLE_USER}, {@code ORACLE_PASSWORD}
 *   <li>{@code PG_JDBC_URL} — e.g. {@code jdbc:postgresql://localhost:5433/tracker}
 *   <li>{@code PG_USER}, {@code PG_PASSWORD}
 * </ul>
 *
 * Optional: {@code ORACLE_SCHEMA} (default {@code SPULIC}), {@code ORACLE_ROBINHOOD_TABLE} (default {@code
 * ROBINHOOD_TRANSACTIONS} within that schema), {@code MIGRATE_TRUNCATE_FIRST} (default {@code true}).
 *
 * <pre>
 * cd server
 * mvn -q compile exec:java -Dexec.mainClass=com.svp.tracker.migration.OracleToPostgresCopy
 * </pre>
 */
public final class OracleToPostgresCopy {

    private OracleToPostgresCopy() {}

    public static void main(String[] args) throws Exception {
        String oracleUrl = env("ORACLE_JDBC_URL");
        String oracleUser = env("ORACLE_USER");
        String oraclePassword = env("ORACLE_PASSWORD");
        String oracleSchema = envOr("ORACLE_SCHEMA", "SPULIC").toUpperCase();
        if (!oracleSchema.matches("[A-Z][A-Z0-9_$#]*")) {
            throw new IllegalArgumentException("Invalid ORACLE_SCHEMA");
        }
        String robinhoodTable = envOr("ORACLE_ROBINHOOD_TABLE", "ROBINHOOD_TRANSACTIONS").toUpperCase();
        if (!robinhoodTable.matches("[A-Z][A-Z0-9_$#]*")) {
            throw new IllegalArgumentException("Invalid ORACLE_ROBINHOOD_TABLE");
        }
        String pgUrl = env("PG_JDBC_URL");
        String pgUser = env("PG_USER");
        String pgPassword = env("PG_PASSWORD");
        boolean truncateFirst = Boolean.parseBoolean(envOr("MIGRATE_TRUNCATE_FIRST", "true"));

        try (Connection oc = DriverManager.getConnection(oracleUrl, oracleUser, oraclePassword);
                Connection pg = DriverManager.getConnection(pgUrl, pgUser, pgPassword)) {
            try (Statement st = oc.createStatement()) {
                st.execute("ALTER SESSION SET CURRENT_SCHEMA = " + oracleSchema);
            }
            pg.setAutoCommit(false);
            if (truncateFirst) {
                truncateNonAuth(pg);
            }

            int n1 = copyManagementCategories(oc, pg);
            int n2 = copyManagementTaskTypes(oc, pg);
            int n3 = copyManagementTasks(oc, pg);
            int n4 = copyFitnessExercises(oc, pg);
            int n5 = copyFitnessDayLogs(oc, pg);
            int n6 = copyFitnessBodyWeight(oc, pg);
            int n7 = copyRobinhood(oc, pg, robinhoodTable);

            resetSequences(pg);
            pg.commit();
            System.out.printf(
                    "Done: categories=%d types=%d tasks=%d exercises=%d day_logs=%d body_weight=%d robinhood=%d%n",
                    n1, n2, n3, n4, n5, n6, n7);
        }
    }

    private static String env(String k) {
        String v = System.getenv(k);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable: " + k);
        }
        return v;
    }

    private static String envOr(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? d : v;
    }

    private static void truncateNonAuth(Connection pg) throws Exception {
        String sql =
                """
                TRUNCATE TABLE auth_mfa_challenges;
                TRUNCATE TABLE auth_trusted_locations;
                TRUNCATE TABLE management_tasks, management_task_types, management_task_categories RESTART IDENTITY CASCADE;
                TRUNCATE TABLE fitness_exercise_day_logs, fitness_exercises RESTART IDENTITY CASCADE;
                TRUNCATE TABLE fitness_body_weight RESTART IDENTITY;
                TRUNCATE TABLE robinhood_transactions;
                """;
        try (Statement st = pg.createStatement()) {
            st.execute(sql);
        }
        System.out.println("Truncated non-auth tables on PostgreSQL.");
    }

    private static int copyManagementCategories(Connection oc, Connection pg) throws Exception {
        String q = "SELECT ID, NAME, DESCRIPTION, CREATED_AT FROM MANAGEMENT_TASK_CATEGORIES ORDER BY ID";
        String ins =
                "INSERT INTO management_task_categories (id, name, description, created_at) VALUES (?, ?, ?, ?)";
        int n = 0;
        try (Statement s = oc.createStatement();
                ResultSet rs = s.executeQuery(q);
                PreparedStatement ps = pg.prepareStatement(ins)) {
            while (rs.next()) {
                ps.setLong(1, rs.getLong("ID"));
                ps.setString(2, rs.getString("NAME"));
                if (rs.getString("DESCRIPTION") == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, rs.getString("DESCRIPTION"));
                }
                ps.setTimestamp(4, Timestamp.from(toInstantUtc(rs.getTimestamp("CREATED_AT"))));
                ps.executeUpdate();
                n++;
            }
        }
        System.out.println("management_task_categories: " + n);
        return n;
    }

    private static int copyManagementTaskTypes(Connection oc, Connection pg) throws Exception {
        String q = "SELECT ID, NAME, NOTES, CREATED_AT FROM MANAGEMENT_TASK_TYPES ORDER BY ID";
        String ins = "INSERT INTO management_task_types (id, name, notes, created_at) VALUES (?, ?, ?, ?)";
        int n = 0;
        try (Statement s = oc.createStatement();
                ResultSet rs = s.executeQuery(q);
                PreparedStatement ps = pg.prepareStatement(ins)) {
            while (rs.next()) {
                ps.setLong(1, rs.getLong("ID"));
                ps.setString(2, rs.getString("NAME"));
                if (rs.getString("NOTES") == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, rs.getString("NOTES"));
                }
                ps.setTimestamp(4, Timestamp.from(toInstantUtc(rs.getTimestamp("CREATED_AT"))));
                ps.executeUpdate();
                n++;
            }
        }
        System.out.println("management_task_types: " + n);
        return n;
    }

    private static int copyManagementTasks(Connection oc, Connection pg) throws Exception {
        String q =
                "SELECT ID, TITLE, NOTES, DUE_DATE, URGENCY, COMPLETED, CATEGORY_ID, TASK_TYPE_ID, CREATED_AT FROM MANAGEMENT_TASKS ORDER BY ID";
        String ins =
                "INSERT INTO management_tasks (id, title, notes, due_date, urgency, completed, category_id, task_type_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int n = 0;
        try (Statement s = oc.createStatement();
                ResultSet rs = s.executeQuery(q);
                PreparedStatement ps = pg.prepareStatement(ins)) {
            while (rs.next()) {
                ps.setLong(1, rs.getLong("ID"));
                ps.setString(2, rs.getString("TITLE"));
                if (rs.getString("NOTES") == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, rs.getString("NOTES"));
                }
                Date dd = rs.getDate("DUE_DATE");
                if (dd == null) {
                    ps.setNull(4, Types.DATE);
                } else {
                    ps.setDate(4, dd);
                }
                ps.setString(5, rs.getString("URGENCY"));
                ps.setBoolean(6, rs.getInt("COMPLETED") != 0);
                ps.setLong(7, rs.getLong("CATEGORY_ID"));
                ps.setLong(8, rs.getLong("TASK_TYPE_ID"));
                ps.setTimestamp(9, Timestamp.from(toInstantUtc(rs.getTimestamp("CREATED_AT"))));
                ps.executeUpdate();
                n++;
            }
        }
        System.out.println("management_tasks: " + n);
        return n;
    }

    private static int copyFitnessExercises(Connection oc, Connection pg) throws Exception {
        String q = "SELECT ID, NAME, CATEGORY, NOTES, CREATED_AT FROM FITNESS_EXERCISES ORDER BY ID";
        String ins = "INSERT INTO fitness_exercises (id, name, category, notes, created_at) VALUES (?, ?, ?, ?, ?)";
        int n = 0;
        try (Statement s = oc.createStatement();
                ResultSet rs = s.executeQuery(q);
                PreparedStatement ps = pg.prepareStatement(ins)) {
            while (rs.next()) {
                ps.setLong(1, rs.getLong("ID"));
                ps.setString(2, rs.getString("NAME"));
                if (rs.getString("CATEGORY") == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, rs.getString("CATEGORY"));
                }
                if (rs.getString("NOTES") == null) {
                    ps.setNull(4, Types.VARCHAR);
                } else {
                    ps.setString(4, rs.getString("NOTES"));
                }
                ps.setTimestamp(5, Timestamp.from(toInstantUtc(rs.getTimestamp("CREATED_AT"))));
                ps.executeUpdate();
                n++;
            }
        }
        System.out.println("fitness_exercises: " + n);
        return n;
    }

    private static int copyFitnessDayLogs(Connection oc, Connection pg) throws Exception {
        String q =
                "SELECT ID, EXERCISE_ID, PERFORMED_ON, NOTES, DURATION_MINUTES FROM FITNESS_EXERCISE_DAY_LOGS ORDER BY ID";
        String ins =
                "INSERT INTO fitness_exercise_day_logs (id, exercise_id, performed_on, notes, duration_minutes) VALUES (?, ?, ?, ?, ?)";
        int n = 0;
        try (Statement s = oc.createStatement();
                ResultSet rs = s.executeQuery(q);
                PreparedStatement ps = pg.prepareStatement(ins)) {
            while (rs.next()) {
                ps.setLong(1, rs.getLong("ID"));
                ps.setLong(2, rs.getLong("EXERCISE_ID"));
                ps.setDate(3, rs.getDate("PERFORMED_ON"));
                ps.setString(4, rs.getString("NOTES"));
                int dm = rs.getInt("DURATION_MINUTES");
                if (rs.wasNull()) {
                    ps.setNull(5, Types.INTEGER);
                } else {
                    ps.setInt(5, dm);
                }
                ps.executeUpdate();
                n++;
            }
        }
        System.out.println("fitness_exercise_day_logs: " + n);
        return n;
    }

    private static int copyFitnessBodyWeight(Connection oc, Connection pg) throws Exception {
        String q = "SELECT ID, LOGGED_ON, WEIGHT_KG, WEIGHT_LB, NOTES FROM FITNESS_BODY_WEIGHT ORDER BY ID";
        String ins = "INSERT INTO fitness_body_weight (id, logged_on, weight_kg, weight_lb, notes) VALUES (?, ?, ?, ?, ?)";
        int n = 0;
        try (Statement s = oc.createStatement();
                ResultSet rs = s.executeQuery(q);
                PreparedStatement ps = pg.prepareStatement(ins)) {
            while (rs.next()) {
                ps.setLong(1, rs.getLong("ID"));
                ps.setDate(2, rs.getDate("LOGGED_ON"));
                ps.setBigDecimal(3, rs.getBigDecimal("WEIGHT_KG"));
                BigDecimal lb = rs.getBigDecimal("WEIGHT_LB");
                if (rs.wasNull()) {
                    ps.setNull(4, Types.NUMERIC);
                } else {
                    ps.setBigDecimal(4, lb);
                }
                if (rs.getString("NOTES") == null) {
                    ps.setNull(5, Types.VARCHAR);
                } else {
                    ps.setString(5, rs.getString("NOTES"));
                }
                ps.executeUpdate();
                n++;
            }
        }
        System.out.println("fitness_body_weight: " + n);
        return n;
    }

    private static int copyRobinhood(Connection oc, Connection pg, String robinhoodTable) throws Exception {
        String q =
                "SELECT ACTIVITY_DATE, PROCESS_DATE, SETTLE_DATE, INSTRUMENT, DESCRIPTION, TRANS_CODE, QUANTITY, PRICE, AMOUNT FROM "
                        + robinhoodTable;
        String ins =
                "INSERT INTO robinhood_transactions (activity_date, process_date, settle_date, instrument, description, trans_code, quantity, price, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int n = 0;
        try (Statement s = oc.createStatement();
                ResultSet rs = s.executeQuery(q);
                PreparedStatement ps = pg.prepareStatement(ins)) {
            while (rs.next()) {
                setNullableTimestamp(ps, 1, rs.getTimestamp("ACTIVITY_DATE"));
                setNullableTimestamp(ps, 2, rs.getTimestamp("PROCESS_DATE"));
                setNullableTimestamp(ps, 3, rs.getTimestamp("SETTLE_DATE"));
                ps.setString(4, rs.getString("INSTRUMENT"));
                ps.setString(5, rs.getString("DESCRIPTION"));
                ps.setString(6, rs.getString("TRANS_CODE"));
                setNullableBigDecimal(ps, 7, rs.getBigDecimal("QUANTITY"));
                setNullableBigDecimal(ps, 8, rs.getBigDecimal("PRICE"));
                setNullableBigDecimal(ps, 9, rs.getBigDecimal("AMOUNT"));
                ps.executeUpdate();
                n++;
            }
        }
        System.out.println("robinhood_transactions: " + n);
        return n;
    }

    private static void setNullableTimestamp(PreparedStatement ps, int i, Timestamp ts) throws Exception {
        if (ts == null) {
            ps.setNull(i, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(i, ts);
        }
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int i, BigDecimal v) throws Exception {
        if (v == null) {
            ps.setNull(i, Types.NUMERIC);
        } else {
            ps.setBigDecimal(i, v);
        }
    }

    private static Instant toInstantUtc(Timestamp ts) {
        if (ts == null) {
            return Instant.now();
        }
        return ts.toInstant();
    }

    private static void resetSequences(Connection pg) throws Exception {
        String[] tables = {
            "management_task_categories",
            "management_task_types",
            "management_tasks",
            "fitness_exercises",
            "fitness_exercise_day_logs",
            "fitness_body_weight"
        };
        try (Statement st = pg.createStatement()) {
            for (String t : tables) {
                st.execute(
                        "SELECT setval(pg_get_serial_sequence('"
                                + t
                                + "', 'id'), COALESCE((SELECT MAX(id) FROM "
                                + t
                                + "), 1), true)");
            }
        }
        System.out.println("Reset id sequences.");
    }
}
