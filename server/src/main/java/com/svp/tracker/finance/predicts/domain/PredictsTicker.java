package com.svp.tracker.finance.predicts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_predicts_tickers")
@Getter
@Setter
@NoArgsConstructor
public class PredictsTicker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "auto_seeded", nullable = false)
    private boolean autoSeeded;

    @Column(name = "sources_enabled", nullable = false, length = 64)
    private String sourcesEnabled = "stocktwits";

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        symbol = normalize(symbol);
        sourcesEnabled = normalizeSources(sourcesEnabled);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        symbol = normalize(symbol);
        sourcesEnabled = normalizeSources(sourcesEnabled);
    }

    public Set<PredictsSource> sourcesEnabledSet() {
        Set<PredictsSource> out = new LinkedHashSet<>();
        for (String token : (sourcesEnabled == null ? "" : sourcesEnabled).split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(PredictsSource.fromWire(t));
            } catch (IllegalArgumentException ignored) {
                // skip unknown entries; clients control valid values
            }
        }
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeSources(String s) {
        if (s == null || s.isBlank()) {
            return "stocktwits";
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(t -> t.toLowerCase(Locale.ROOT))
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("stocktwits");
    }
}
