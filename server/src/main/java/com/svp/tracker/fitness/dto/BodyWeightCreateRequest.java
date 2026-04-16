package com.svp.tracker.fitness.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.svp.tracker.fitness.domain.BodyWeightLog;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * JSON body for POST /api/fitness/body-weight. Separate from {@link BodyWeightLog} so Jackson binds
 * {@code weightLb} (and snake_case aliases) before persistence to {@code weight_lb}.
 */
@Data
public class BodyWeightCreateRequest {

    @NotNull
    private LocalDate loggedOn;

    @NotNull
    private BigDecimal weightKg;

    @JsonProperty("weightLb")
    @JsonAlias({"weight_lb", "weight"})
    private BigDecimal weightLb;

    private String notes;

    public BodyWeightLog toEntity() {
        BodyWeightLog e = new BodyWeightLog();
        e.setLoggedOn(loggedOn);
        e.setWeightKg(weightKg);
        e.setWeightLb(weightLb);
        e.setNotes(notes);
        return e;
    }
}
