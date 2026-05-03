package com.svp.tracker.auth.tool;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Offline helper: prints SQL to reset a user's password using the same algorithm as {@link
 * com.svp.tracker.auth.service.PasswordHashService} (BCrypt of {@code password + "::" + salt + "::" + pepper}).
 *
 * <pre>
 * cd server
 * mvn -q compile exec:java -Dexec.mainClass=com.svp.tracker.auth.tool.PasswordHashCli \
 *   "-Dexec.args=YourNewPassword your-pepper-from-env"
 *
 * # Or pass pepper only via env (matches TRACKER_AUTH_PASSWORD_PEPPER in .env.stack / application.yml):
 * TRACKER_AUTH_PASSWORD_PEPPER='your-pepper' mvn -q compile exec:java \
 *   -Dexec.mainClass=com.svp.tracker.auth.tool.PasswordHashCli "-Dexec.args=YourNewPassword"
 *
 * </pre>
 */
public final class PasswordHashCli {

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println(
                    """
                    Usage: PasswordHashCli <newPassword> [pepper]
                      pepper: optional; else env TRACKER_AUTH_PASSWORD_PEPPER; if unset, same default as application.yml (tracker-dev-pepper).
                    Prints a PostgreSQL UPDATE for user admin. Edit the WHERE clause for other users.
                    """);
            System.exit(1);
        }
        String rawPassword = args[0];
        String pepper = args.length > 1 ? args[1] : AuthCliDefaults.passwordPepper();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(AuthCliDefaults.bcryptStrength());
        String salt = randomSalt();
        String toHash = rawPassword + "::" + salt + "::" + pepper;
        String hash = encoder.encode(toHash);

        String escHash = hash.replace("'", "''");
        String escSalt = salt.replace("'", "''");

        System.out.println("-- Reset admin password (matches PasswordHashService + same pepper as the API).");
        System.out.println("UPDATE auth_users");
        System.out.println("SET password_hash = '" + escHash + "',");
        System.out.println("    password_salt = '" + escSalt + "'");
        System.out.println("WHERE LOWER(username) = LOWER('admin');");
    }

    private static String randomSalt() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
