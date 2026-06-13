package com.svp.tracker.finance.dto.admin;

import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeEvaluateDto;

public record RobinhoodAgenticAdminActionResultDto(
        boolean ok, String message, RobinhoodAgenticAutoTradeEvaluateDto evaluateResult) {

    public static RobinhoodAgenticAdminActionResultDto ok(String message) {
        return new RobinhoodAgenticAdminActionResultDto(true, message, null);
    }

    public static RobinhoodAgenticAdminActionResultDto ok(String message, RobinhoodAgenticAutoTradeEvaluateDto result) {
        return new RobinhoodAgenticAdminActionResultDto(true, message, result);
    }
}
