package com.svp.tracker.management.dto;

import java.util.List;

public record ManagementRecordingUploadResultDto(
        List<ManagementRecordingItemDto> recordings, int imageCount) {}
