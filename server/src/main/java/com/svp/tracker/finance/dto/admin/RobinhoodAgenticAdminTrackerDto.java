package com.svp.tracker.finance.dto.admin;

import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeRunDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderDto;
import java.util.List;

public record RobinhoodAgenticAdminTrackerDto(
        List<RobinhoodAgenticAdminOrderRowDto> pendingOrders,
        List<RobinhoodAgenticOrderDto> recentOrders,
        List<RobinhoodAgenticAutoTradeRunDto> recentRuns,
        List<RobinhoodAgenticApprovalNotificationDto> recentNotifications) {}
