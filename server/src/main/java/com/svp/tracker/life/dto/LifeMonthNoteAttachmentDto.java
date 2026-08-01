package com.svp.tracker.life.dto;

public record LifeMonthNoteAttachmentDto(
        long id, String originalFilename, String contentType, long sizeBytes, String downloadPath) {}
