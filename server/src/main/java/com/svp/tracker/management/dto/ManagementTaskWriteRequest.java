package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.BalanceUrgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ManagementTaskWriteRequest {

    @NotBlank
    private String title;

    private String notes;

    private LocalDate dueDate;

    @NotNull
    private BalanceUrgency urgency = BalanceUrgency.MEDIUM;

    private Long categoryId;

    private Long taskTypeId;

    private Boolean completed;
}
