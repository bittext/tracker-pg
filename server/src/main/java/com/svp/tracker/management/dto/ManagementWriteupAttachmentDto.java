package com.svp.tracker.management.dto;

public record ManagementWriteupAttachmentDto(
        long id, String originalFilename, String contentType, long sizeBytes, String downloadPath) {}
