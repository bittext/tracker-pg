package com.svp.tracker.management.dto;

import java.util.List;

public record ManagementRecordingListDto(
        boolean enabled,
        String rootPath,
        String note,
        List<ManagementRecordingDayDto> days,
        List<ManagementRecordingItemDto> recordings) {}
