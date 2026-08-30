package com.svp.tracker.management.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "management_writeups")
@Getter
@Setter
@NoArgsConstructor
public class ManagementWriteup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private int year;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String topic = "";

    @Column(name = "topic_group", columnDefinition = "TEXT")
    private String topicGroup;

    @Column(name = "topic_group_sort", nullable = false)
    private int topicGroupSort = 0;

    @Column(columnDefinition = "TEXT")
    private String highlight;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(
            mappedBy = "writeup",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<ManagementWriteupAttachment> attachments = new ArrayList<>();
}
