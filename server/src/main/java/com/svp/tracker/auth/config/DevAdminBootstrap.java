package com.svp.tracker.auth.config;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.domain.AppUserRole;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.service.PasswordHashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DevAdminBootstrap implements ApplicationRunner {

    private final AuthProperties authProperties;
    private final AppUserRepository appUserRepository;
    private final PasswordHashService passwordHashService;

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
        appUserRepository.save(admin);

        log.warn(
                "Bootstrapped default admin user '{}' for local development. Change password after first login.",
                authProperties.bootstrapAdminUsername());
    }
}
