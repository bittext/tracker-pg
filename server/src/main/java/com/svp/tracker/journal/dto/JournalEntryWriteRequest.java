package com.svp.tracker.journal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class JournalEntryWriteRequest {

    @NotNull
    private LocalDate loggedOn;

    @NotBlank
    private String bodyMarkdown;

    private List<Long> tagIds;
}
