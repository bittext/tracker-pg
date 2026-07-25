package com.svp.tracker.management.dto;

/** Result of queueing a user's uploaded recordings for fresh transcription and summary. */
public record ManagementRecordingReprocessDto(int queuedCount) {}
