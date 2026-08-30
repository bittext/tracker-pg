package com.svp.tracker.management.dto;

import java.util.List;

public record ManagementWriteupDto(
        long id,
        long ownerUserId,
        int year,
        String topic,
        String topicGroup,
        int topicGroupSort,
        int topicGroupRank,
        String highlight,
        String body,
        List<ManagementWriteupAttachmentDto> attachments,
        String createdAt,
        String updatedAt) {}
