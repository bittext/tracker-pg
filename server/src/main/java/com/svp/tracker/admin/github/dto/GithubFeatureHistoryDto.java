package com.svp.tracker.admin.github.dto;

import java.util.List;

/** Commit-derived feature timeline for Admin → Features (read-only GitHub history). */
public record GithubFeatureHistoryDto(
        GithubRepoSummaryDto repository,
        List<GithubFeatureHistoryEntryDto> entries,
        int totalCommitsFetched,
        int mergeCommitsOmitted,
        boolean moreCommitsAvailable,
        String sourceNote,
        List<String> warnings) {}
