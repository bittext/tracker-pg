package com.svp.tracker.admin.github.dto;

import java.util.List;

public record GithubRepositoryInsightsDto(
        GithubRepoSummaryDto repository,
        List<GithubContributorDto> contributors,
        List<GithubUserRefDto> subscribers,
        String subscribersNote,
        List<GithubUserRefDto> stargazersSample,
        String stargazersNote,
        List<GithubCommitSummaryDto> recentCommits,
        List<GithubUserRefDto> recentCommitAuthors,
        GithubTrafficSummaryDto trafficViews,
        String trafficNote,
        List<String> warnings) {}
