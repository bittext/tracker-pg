package com.svp.tracker.finance.predicts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_predicts_source_health")
@Getter
@Setter
@NoArgsConstructor
public class PredictsSourceHealth {

    @Id
    @Column(length = 32, nullable = false)
    private String source;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "mentions_ingested_24h", nullable = false)
    private int mentionsIngested24h;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
