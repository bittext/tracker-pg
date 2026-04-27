package com.svp.tracker.management.dto;

public record ManagementMonthNoteAttachmentDto(
        long id, String originalFilename, String contentType, long sizeBytes, String downloadPath) {}
