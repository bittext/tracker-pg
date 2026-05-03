package com.svp.tracker.admin.github.dto;

public record GithubRepoSummaryDto(
        String fullName,
        String htmlUrl,
        String description,
        String defaultBranch,
        String pushedAt,
        long stargazersCount,
        long forksCount,
        long subscribersCount,
        long watchersCount,
        long openIssuesCount) {}
