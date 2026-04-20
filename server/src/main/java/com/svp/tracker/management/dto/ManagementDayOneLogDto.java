package com.svp.tracker.management.dto;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManagementDayOneLogDto {
    Long id;
    LocalDate loggedOn;
    String entryText;
    Instant createdAt;
    Instant updatedAt;
}
