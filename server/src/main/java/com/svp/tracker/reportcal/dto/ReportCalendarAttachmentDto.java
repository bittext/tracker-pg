package com.svp.tracker.reportcal.dto;

public record ReportCalendarAttachmentDto(
        long id, String originalFilename, String contentType, long sizeBytes, String downloadPath) {}
