package com.svp.tracker.admin.github.dto;

import java.util.List;

public record GithubTrafficSummaryDto(int totalViews, int totalUniques, List<GithubTrafficDayDto> days) {}
