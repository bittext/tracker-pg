package com.svp.tracker.admin.github.dto;

public record GithubCommitSummaryDto(
        String shaShort,
        String messageFirstLine,
        String messageFull,
        String authorLogin,
        String authorName,
        String committedAt,
        String htmlUrl) {}
