package com.svp.tracker.finance.dto.admin;

import java.time.Instant;

public record RobinhoodAgenticApprovalNotificationDto(
        long id,
        long ownerUserId,
        long orderId,
        String channel,
        String status,
        String destinationMasked,
        String detail,
        Instant createdAt) {}
