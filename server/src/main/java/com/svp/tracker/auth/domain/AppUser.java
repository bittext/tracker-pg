package com.svp.tracker.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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
    @Column(nullable = false, unique = true, length = 120)
    private String username;

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

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
