package com.svp.tracker.management.dto;

import java.util.List;

public record ManagementRecordingListDto(
        boolean enabled,
        /** Always "cloud" for the uploaded library. */
        String storageMode,
        String note,
        List<ManagementRecordingDayDto> days,
        List<ManagementRecordingItemDto> recordings) {}
