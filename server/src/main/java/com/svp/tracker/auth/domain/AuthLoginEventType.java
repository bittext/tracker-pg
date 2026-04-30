package com.svp.tracker.auth.domain;

public enum AuthLoginEventType {
    /** JWT returned (after password, trusted device, or completed MFA). Use {@code detail} to distinguish, e.g. mfa, trusted_location. */
    LOGIN_SUCCESS,
    /** SMS MFA challenge created; user must call /api/auth/mfa/verify. */
    MFA_REQUIRED,
    /** Unknown / inactive user, or wrong password. */
    LOGIN_FAILED,
    /** Wrong, expired, or maxed-out MFA challenge. */
    MFA_FAILED,
    /** Client called POST /api/auth/logout (stateless; best-effort with JWT). */
    LOGOUT
}
