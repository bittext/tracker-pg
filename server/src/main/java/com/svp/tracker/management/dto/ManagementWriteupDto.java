package com.svp.tracker.management.dto;

public record ManagementWriteupDto(
        long id,
        long ownerUserId,
        int year,
        String topic,
        String highlight,
        String body,
        String createdAt,
        String updatedAt) {}
