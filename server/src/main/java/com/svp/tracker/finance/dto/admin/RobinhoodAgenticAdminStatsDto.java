package com.svp.tracker.finance.dto.admin;

public record RobinhoodAgenticAdminStatsDto(
        long connectedUsers,
        long pendingApprovals,
        long ordersLast24h,
        long autoTradeRunsLast24h,
        long autoTradeEnabledUsers,
        long notificationsLast24h) {}
