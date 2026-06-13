package com.svp.tracker.finance.dto.admin;

import com.svp.tracker.finance.dto.RobinhoodAgenticOrderDto;

public record RobinhoodAgenticAdminOrderRowDto(
        RobinhoodAgenticOrderDto order,
        long ownerUserId,
        String ownerUsername,
        String ownerEmail) {}
