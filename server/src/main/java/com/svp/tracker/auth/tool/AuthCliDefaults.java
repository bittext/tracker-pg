package com.svp.tracker.auth.tool;

/**
 * Offline CLI defaults aligned with {@code application.yml} so hashes match {@link
 * com.svp.tracker.auth.service.PasswordHashService} when env vars are unset (typical local / demo runs).
 */
public final class AuthCliDefaults {

    private AuthCliDefaults() {}

    /**
     * Same default as {@code tracker.auth.password-pepper} when {@code TRACKER_AUTH_PASSWORD_PEPPER} is not set in the
     * environment. If the variable is set (including empty string), that value is used.
     */
    public static String passwordPepper() {
        String v = System.getenv("TRACKER_AUTH_PASSWORD_PEPPER");
        return v != null ? v : "tracker-dev-pepper";
    }

    /** Same default as {@code tracker.auth.bcrypt-strength} (12). */
    public static int bcryptStrength() {
        String raw = System.getenv("TRACKER_AUTH_BCRYPT_STRENGTH");
        if (raw == null || raw.isBlank()) {
            return 12;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            return Math.min(31, Math.max(4, n));
        } catch (NumberFormatException e) {
            return 12;
        }
    }
}
