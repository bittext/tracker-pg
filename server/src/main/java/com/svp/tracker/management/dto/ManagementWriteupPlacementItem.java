package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Placement-only update (group membership and order). Does not rewrite topic or body. */
public record ManagementWriteupPlacementItem(
        @NotNull Long id, @Size(max = 2000) String topicGroup, @NotNull Integer topicGroupSort) {}
