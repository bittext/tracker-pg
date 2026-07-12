package com.svp.tracker.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String username;

    /**
     * Primary account email (admin provisioning, contact). Normalized to trimmed lowercase. Optional for legacy rows;
     * must not duplicate another account’s email (see Flyway {@code ux_auth_users_email_lower} and member_profiles).
     */
    @Size(max = 320)
    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 256)
    private String passwordHash;

    @Column(nullable = false, length = 128)
    private String passwordSalt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppUserRole role = AppUserRole.USER;

    @Column(name = "phone_e164", length = 32)
    private String phoneE164;

    @Column(nullable = false)
    private boolean mfaEnabled = true;

    @Column(nullable = false)
    private boolean active = true;

    /** When true, user may open Markets shell and call trading APIs. ADMIN always allowed. */
    @Column(name = "markets_enabled", nullable = false)
    private boolean marketsEnabled = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** When set, first-login onboarding (credentials → profile → member id) is finished. */
    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    /** User finished the username/password step and may capture profile under Admin. */
    @Column(name = "credentials_step_completed_at")
    private Instant credentialsStepCompletedAt;

    /**
     * Stable public identifier (digits) shown to the member; minted when profile is first saved. Distinct from {@link
     * #id} (internal surrogate).
     */
    @Column(name = "member_public_id", unique = true)
    private Long memberPublicId;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        normalizeUsername();
        normalizeEmail();
    }

    @PreUpdate
    void onUpdate() {
        normalizeUsername();
        normalizeEmail();
    }

    private void normalizeUsername() {
        if (username != null) {
            username = username.trim().toLowerCase(Locale.ROOT);
        }
    }

    private void normalizeEmail() {
        if (email == null) {
            return;
        }
        String t = email.trim().toLowerCase(Locale.ROOT);
        email = t.isEmpty() ? null : t;
    }
}
