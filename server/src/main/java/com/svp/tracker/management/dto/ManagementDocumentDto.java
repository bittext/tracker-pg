package com.svp.tracker.management.dto;

/** Member-scoped document row (no storage key exposed). */
public record ManagementDocumentDto(
        long id,
        String displayName,
        String docType,
        String originalFilename,
        String contentType,
        long byteSize,
        String downloadPath,
        String createdAt,
        String updatedAt) {}
