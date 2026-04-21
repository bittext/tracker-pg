package com.svp.tracker.journal.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JournalTagDefDto {
    long id;
    String name;
    Instant createdAt;
}
