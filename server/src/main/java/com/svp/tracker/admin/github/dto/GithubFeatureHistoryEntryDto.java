package com.svp.tracker.admin.github.dto;

/** One shipped change derived from a GitHub commit (subject line + metadata). */
public record GithubFeatureHistoryEntryDto(
        String shaShort,
        String featureSummary,
        String messageFirstLine,
        String messageFull,
        String authorLogin,
        String authorName,
        String committedAt,
        String htmlUrl) {}
