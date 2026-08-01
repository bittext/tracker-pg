package com.svp.tracker.trackernotes.dto;

public record TrackerMonthNoteAttachmentDto(
        long id, String originalFilename, String contentType, long sizeBytes, String downloadPath) {}
