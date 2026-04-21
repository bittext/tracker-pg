package com.svp.tracker.management.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManagementDayOneTagDefDto {
    Long id;
    String name;
    Instant createdAt;
}
