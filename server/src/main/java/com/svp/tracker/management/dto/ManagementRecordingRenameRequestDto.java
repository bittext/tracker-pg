package com.svp.tracker.management.dto;

/** Rename a recording's display label in Tracker (does not change iCloud / Just Press Record). */
public record ManagementRecordingRenameRequestDto(String path, String displayName) {}
