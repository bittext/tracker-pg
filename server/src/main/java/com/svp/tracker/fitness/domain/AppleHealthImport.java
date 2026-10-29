package com.svp.tracker.fitness.domain;

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
@Table(name = "fitness_apple_health_import")
@Getter
@Setter
@NoArgsConstructor
public class AppleHealthImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "workouts_added", nullable = false)
    private int workoutsAdded;

    @Column(name = "weights_added", nullable = false)
    private int weightsAdded;
}
