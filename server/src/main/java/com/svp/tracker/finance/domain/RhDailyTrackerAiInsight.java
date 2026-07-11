package com.svp.tracker.finance.domain;

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
@Table(name = "rh_daily_tracker_ai_insight")
@Getter
@Setter
@NoArgsConstructor
public class RhDailyTrackerAiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    @Column(name = "period_key", nullable = false, length = 32)
    private String periodKey;

    @Column(name = "facts_hash", nullable = false, length = 64)
    private String factsHash;

    @Column(name = "insight_json", nullable = false, columnDefinition = "TEXT")
    private String insightJson;

    @Column(name = "model", nullable = false, length = 128)
    private String model = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
