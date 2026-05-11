package com.svp.tracker.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "management_accounts")
@Getter
@Setter
@NoArgsConstructor
public class ManagementAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "item_name", nullable = false, columnDefinition = "TEXT")
    private String itemName = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String folder = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String username = "";

    /** Sealed (AES-GCM) or plaintext (when encryption key unset). Never expose this column directly to clients. */
    @Column(name = "password_enc", nullable = false, columnDefinition = "TEXT")
    private String passwordEnc = "";

    /** Sealed (AES-GCM) or plaintext (when encryption key unset). Never expose this column directly to clients. */
    @Column(name = "authenticator_key_enc", nullable = false, columnDefinition = "TEXT")
    private String authenticatorKeyEnc = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String website = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
