package com.svp.tracker.finance.dto;

import java.util.List;

/** Full daily series for one option contract (used when listing all owned contracts). */
public record RobinhoodOwnershipContractSeriesDto(
        RobinhoodOwnershipContractDto contract, List<RobinhoodOwnershipHistoryPointDto> points) {}
