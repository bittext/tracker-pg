package com.svp.tracker.auth.security;

import com.svp.tracker.auth.domain.AppUserRole;

/** Principal stored in {@link org.springframework.security.core.context.SecurityContext} after JWT validation. */
public record TrackerUserPrincipal(long id, String username, AppUserRole role, boolean marketsEnabled) {

    public boolean isAdmin() {
        return role == AppUserRole.ADMIN;
    }

    /** ADMIN always has Markets; otherwise requires the per-user flag. */
    public boolean canAccessMarkets() {
        return isAdmin() || marketsEnabled;
    }
}
