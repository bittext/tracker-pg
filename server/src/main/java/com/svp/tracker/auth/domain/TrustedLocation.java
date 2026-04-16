package com.svp.tracker.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_trusted_locations")
@Getter
@Setter
@NoArgsConstructor
public class TrustedLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 128)
    private String locationHash;

    @Column(length = 180)
    private String displayLabel;

    @Column(nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (firstSeenAt == null) {
            firstSeenAt = Instant.now();
        }
        if (lastSeenAt == null) {
            lastSeenAt = Instant.now();
        }
    }
}
