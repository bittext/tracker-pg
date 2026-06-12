package com.svp.tracker.finance.dto;

import java.util.List;

public record RobinhoodAgenticPositionsDto(List<RobinhoodAgenticPositionDto> positions, String portfolioJson) {}
