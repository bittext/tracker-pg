package com.svp.tracker.auth.tool;

import com.svp.tracker.auth.domain.AppUserRole;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
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
 *
 * When TRACKER_UPSERT_PASSWORD is set (non-empty), the password is read from that env var and exec.args must omit it:
 *   TRACKER_UPSERT_PASSWORD='secret' mvn ... "-Dexec.args=demo USER false true -"
 * Shell scripts should use this form so passwords containing {@code $} are not mangled by bash when building {@code -Dexec.args}.
 * </pre>
 */
public final class UserUpsertSqlCli {

    private static final String UPSERT_PASSWORD_ENV = "TRACKER_UPSERT_PASSWORD";

    private UserUpsertSqlCli() {}

    public static void main(String[] args) {
        String envPassword = System.getenv(UPSERT_PASSWORD_ENV);
        boolean passwordFromEnv = envPassword != null && !envPassword.isEmpty();

        if (passwordFromEnv) {
            if (args.length < 1 || args.length > 6) {
                System.err.println(
                        """
                        Usage (with %s set): UserUpsertSqlCli <username> [role] [mfaEnabled] [active] [phoneE164] [pepper]
                          role: ADMIN or USER (default USER)
                          mfaEnabled: true|false (default false)
                          active: true|false (default true)
                          phoneE164: optional, use '-' for blank
                          pepper: optional; else env TRACKER_AUTH_PASSWORD_PEPPER; if unset, same default as application.yml (tracker-dev-pepper)
                        """
                                .formatted(UPSERT_PASSWORD_ENV));
                System.exit(1);
            }
        } else if (args.length < 2 || args.length > 7) {
            System.err.println(
                    """
                    Usage: UserUpsertSqlCli <username> <password> [role] [mfaEnabled] [active] [phoneE164] [pepper]
                      role: ADMIN or USER (default USER)
                      mfaEnabled: true|false (default false)
                      active: true|false (default true)
                      phoneE164: optional, use '-' for blank
                      pepper: optional; else env TRACKER_AUTH_PASSWORD_PEPPER; if unset, same default as application.yml (tracker-dev-pepper)
                    Or set %s for the password and pass only username and optional trailing args (see scripts/create-demo-user.sh).
                    """
                            .formatted(UPSERT_PASSWORD_ENV));
            System.exit(1);
        }

        String username = args[0].trim().toLowerCase(Locale.ROOT);
        String rawPassword = passwordFromEnv ? envPassword : args[1];
        AppUserRole role =
                parseRole(passwordFromEnv ? (args.length > 1 ? args[1] : "USER") : (args.length > 2 ? args[2] : "USER"));
        boolean mfaEnabled =
                parseBoolean(
                        passwordFromEnv ? (args.length > 2 ? args[2] : "false") : (args.length > 3 ? args[3] : "false"),
                        "mfaEnabled");
        boolean active =
                parseBoolean(
                        passwordFromEnv ? (args.length > 3 ? args[3] : "true") : (args.length > 4 ? args[4] : "true"),
                        "active");
        String phoneE164 =
                normalizePhone(
                        passwordFromEnv ? (args.length > 4 ? args[4] : "") : (args.length > 5 ? args[5] : ""));
        String pepper =
                passwordFromEnv
                        ? (args.length > 5 ? args[5] : AuthCliDefaults.passwordPepper())
                        : (args.length > 6 ? args[6] : AuthCliDefaults.passwordPepper());

        if (username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(AuthCliDefaults.bcryptStrength());
        String salt = randomSalt();
        String toHash = rawPassword + "::" + salt + "::" + pepper;
        String hash = encoder.encode(toHash);

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
        System.out.println("ON CONFLICT ((LOWER(TRIM(username)))) DO UPDATE");
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
