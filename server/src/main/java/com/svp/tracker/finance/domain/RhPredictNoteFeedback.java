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
@Table(name = "rh_predict_note_feedback")
@Getter
@Setter
@NoArgsConstructor
public class RhPredictNoteFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "headline", nullable = false, columnDefinition = "TEXT")
    private String headline = "";

    @Column(name = "points_json", nullable = false, columnDefinition = "TEXT")
    private String pointsJson = "[]";

    @Column(name = "watch", nullable = false, columnDefinition = "TEXT")
    private String watch = "";

    @Column(name = "model", nullable = false, length = 128)
    private String model = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
