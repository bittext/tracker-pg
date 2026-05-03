package com.svp.tracker.admin.github.dto;

public record GithubCommitSummaryDto(
        String shaShort,
        String messageFirstLine,
        String authorLogin,
        String authorName,
        String committedAt,
        String htmlUrl) {}
