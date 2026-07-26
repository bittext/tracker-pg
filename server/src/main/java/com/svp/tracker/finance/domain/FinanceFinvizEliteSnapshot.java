package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_finviz_elite_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class FinanceFinvizEliteSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, length = 512)
    private String cacheKey;

    @Column(name = "source_label", length = 256)
    private String sourceLabel;

    @Column(name = "columns_json", nullable = false, columnDefinition = "TEXT")
    private String columnsJson;

    @Column(name = "rows_json", nullable = false, columnDefinition = "TEXT")
    private String rowsJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() {
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
    }
}
