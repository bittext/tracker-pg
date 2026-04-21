package com.svp.tracker.management.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "management_day_one_logs")
public class ManagementDayOneLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "logged_on", nullable = false)
    private LocalDate loggedOn;

    @Column(name = "entry_text", nullable = false, columnDefinition = "TEXT")
    private String entryText;

    @Column(name = "location_text", length = 512)
    private String locationText;

    @Column(name = "weather_text", length = 512)
    private String weatherText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "management_day_one_log_tags",
            joinColumns = @JoinColumn(name = "log_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_def_id"))
    private Set<ManagementDayOneTagDef> tags = new HashSet<>();

    @OneToMany(mappedBy = "log", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ManagementDayOneAttachment> attachments = new HashSet<>();
}
