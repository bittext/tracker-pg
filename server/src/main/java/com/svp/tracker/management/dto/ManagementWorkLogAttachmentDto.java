package com.svp.tracker.management.dto;

public record ManagementWorkLogAttachmentDto(
        long id, String originalFilename, String contentType, long sizeBytes, String downloadPath) {}
