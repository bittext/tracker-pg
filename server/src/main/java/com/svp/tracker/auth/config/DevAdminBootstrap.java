package com.svp.tracker.auth.config;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.domain.AppUserRole;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.service.PasswordHashService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DevAdminBootstrap implements ApplicationRunner {

    private final AuthProperties authProperties;
    private final AppUserRepository appUserRepository;
    private final PasswordHashService passwordHashService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!authProperties.bootstrapAdminEnabled()) {
            return;
        }
        if (appUserRepository.count() > 0) {
            return;
        }

        var passwordHash = passwordHashService.create(authProperties.bootstrapAdminPassword());
        AppUser admin = new AppUser();
        admin.setUsername(authProperties.bootstrapAdminUsername());
        admin.setPasswordHash(passwordHash.hash());
        admin.setPasswordSalt(passwordHash.salt());
        admin.setRole(AppUserRole.ADMIN);
        admin.setMfaEnabled(false);
        admin.setActive(true);
        admin.setMarketsEnabled(true);
        Instant now = Instant.now();
        admin.setCredentialsStepCompletedAt(now);
        // Leave onboarding_completed_at null until member profile + member-ID step (same as other accounts).
        appUserRepository.save(admin);
        backfillOwnerUserIdForNullRows(admin.getId());

        log.warn(
                "Bootstrapped default admin user '{}' for local development. Change password after first login.",
                authProperties.bootstrapAdminUsername());
    }

    /**
     * If Flyway added {@code owner_user_id} before any user existed, legacy rows may still be NULL. Attach them to
     * the first bootstrapped admin so per-user scoping has a defined owner.
     */
    private void backfillOwnerUserIdForNullRows(long adminId) {
        String[] tables = {
            "management_task_categories",
            "management_task_types",
            "management_tasks",
            "fitness_exercises",
            "fitness_exercise_day_logs",
            "fitness_body_weight",
            "robinhood_transactions"
        };
        for (String tbl : tables) {
            try {
                int n = jdbcTemplate.update(
                        "UPDATE " + tbl + " SET owner_user_id = ? WHERE owner_user_id IS NULL", adminId);
                if (n > 0) {
                    log.info("Backfilled owner_user_id on {} row(s) in {}", n, tbl);
                }
            } catch (RuntimeException e) {
                log.debug("Skipping owner backfill for {}: {}", tbl, e.getMessage());
            }
        }
    }
}
