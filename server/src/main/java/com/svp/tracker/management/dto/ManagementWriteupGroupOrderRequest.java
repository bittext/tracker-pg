package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Manual ordering of topic groups within a year. groupLabels is the full set in display order. */
public record ManagementWriteupGroupOrderRequest(
        @NotNull Integer year, @NotEmpty List<String> groupLabels) {}
