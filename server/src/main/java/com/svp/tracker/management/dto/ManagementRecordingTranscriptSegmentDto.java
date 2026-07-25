package com.svp.tracker.management.dto;

/** One timed turn in a recording transcript (speaker optional). */
public record ManagementRecordingTranscriptSegmentDto(
        String speaker, String text, Double startSeconds, Double endSeconds) {}
