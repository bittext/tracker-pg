package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.BalanceUrgency;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagementTaskDto {

    private Long id;
    private String title;
    private String notes;
    private LocalDate dueDate;
    private BalanceUrgency urgency;
    private boolean completed;
    private Long categoryId;
    private String categoryName;
    private Long taskTypeId;
    private String taskTypeName;
    private Instant createdAt;
}
