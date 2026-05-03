package com.svp.tracker.admin.github.dto;

public record GithubContributorDto(String login, String htmlUrl, String avatarUrl, int contributions) {}
