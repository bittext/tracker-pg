package com.svp.tracker.auth.tool;

import com.svp.tracker.auth.domain.AppUserRole;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Offline helper that prints PostgreSQL SQL to create or update a user.
 *
 * <p>Hashing matches PasswordHashService: BCrypt(password + "::" + salt + "::" + pepper).
 *
 * <pre>
 * cd server
 * TRACKER_AUTH_PASSWORD_PEPPER='your-pepper' mvn -q compile exec:java \
 *   -Dexec.mainClass=com.svp.tracker.auth.tool.UserUpsertSqlCli \
 *   "-Dexec.args=demo demo123 USER false true"
 * </pre>
 */
public final class UserUpsertSqlCli {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private UserUpsertSqlCli() {}

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 7) {
            System.err.println(
                    """
                    Usage: UserUpsertSqlCli <username> <password> [role] [mfaEnabled] [active] [phoneE164] [pepper]
                      role: ADMIN or USER (default USER)
                      mfaEnabled: true|false (default false)
                      active: true|false (default true)
                      phoneE164: optional, use '-' for blank
                      pepper: optional, defaults to env TRACKER_AUTH_PASSWORD_PEPPER (or empty)
                    """);
            System.exit(1);
        }

        String username = args[0].trim();
        String rawPassword = args[1];
        AppUserRole role = parseRole(args.length > 2 ? args[2] : "USER");
        boolean mfaEnabled = parseBoolean(args.length > 3 ? args[3] : "false", "mfaEnabled");
        boolean active = parseBoolean(args.length > 4 ? args[4] : "true", "active");
        String phoneE164 = normalizePhone(args.length > 5 ? args[5] : "");
        String pepper = args.length > 6 ? args[6] : System.getenv().getOrDefault("TRACKER_AUTH_PASSWORD_PEPPER", "");

        if (username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }

        String salt = randomSalt();
        String toHash = rawPassword + "::" + salt + "::" + pepper;
        String hash = ENCODER.encode(toHash);

        String escUsername = sqlQuote(username);
        String escHash = sqlQuote(hash);
        String escSalt = sqlQuote(salt);
        String escRole = sqlQuote(role.name());
        String phoneSql = phoneE164.isBlank() ? "NULL" : "'" + sqlQuote(phoneE164) + "'";

        System.out.println("-- Upsert auth user (hashing matches PasswordHashService with current pepper).");
        System.out.println("INSERT INTO auth_users");
        System.out.println("  (username, password_hash, password_salt, role, phone_e164, mfa_enabled, active, created_at)");
        System.out.println("VALUES");
        System.out.println("  ('" + escUsername + "', '" + escHash + "', '" + escSalt + "', '" + escRole + "', "
                + phoneSql + ", " + mfaEnabled + ", " + active + ", NOW())");
        System.out.println("ON CONFLICT (username) DO UPDATE");
        System.out.println("SET password_hash = EXCLUDED.password_hash,");
        System.out.println("    password_salt = EXCLUDED.password_salt,");
        System.out.println("    role = EXCLUDED.role,");
        System.out.println("    phone_e164 = EXCLUDED.phone_e164,");
        System.out.println("    mfa_enabled = EXCLUDED.mfa_enabled,");
        System.out.println("    active = EXCLUDED.active;");
    }

    private static AppUserRole parseRole(String raw) {
        try {
            return AppUserRole.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("role must be ADMIN or USER");
        }
    }

    private static boolean parseBoolean(String raw, String label) {
        String v = raw.trim().toLowerCase();
        if ("true".equals(v)) {
            return true;
        }
        if ("false".equals(v)) {
            return false;
        }
        throw new IllegalArgumentException(label + " must be true or false");
    }

    private static String normalizePhone(String raw) {
        String p = raw == null ? "" : raw.trim();
        if (p.isBlank() || "-".equals(p)) {
            return "";
        }
        return p;
    }

    private static String randomSalt() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sqlQuote(String value) {
        return value.replace("'", "''");
    }
}
