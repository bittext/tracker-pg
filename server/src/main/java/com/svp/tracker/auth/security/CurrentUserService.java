package com.svp.tracker.auth.security;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUserService {

    public Optional<TrackerUserPrincipal> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object p = auth.getPrincipal();
        if (p instanceof TrackerUserPrincipal tu) {
            return Optional.of(tu);
        }
        return Optional.empty();
    }

    public boolean isAdmin() {
        return currentUser().map(TrackerUserPrincipal::isAdmin).orElse(false);
    }

    public long requireUserId() {
        return currentUser()
                .map(TrackerUserPrincipal::id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
