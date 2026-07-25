package com.svp.tracker.management.dto;

/** Result of clearing leftover auto-queue statuses (or similar bulk recording ops). */
public record ManagementRecordingReprocessDto(int clearedCount) {}
