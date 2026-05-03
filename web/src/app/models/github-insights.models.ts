export interface GithubRepoSummaryDto {
  fullName: string;
  htmlUrl: string;
  description: string;
  defaultBranch: string;
  pushedAt: string;
  stargazersCount: number;
  forksCount: number;
  subscribersCount: number;
  watchersCount: number;
  openIssuesCount: number;
}

export interface GithubContributorDto {
  login: string;
  htmlUrl: string;
  avatarUrl: string;
  contributions: number;
}

export interface GithubUserRefDto {
  login: string;
  htmlUrl: string;
  avatarUrl: string;
}

export interface GithubCommitSummaryDto {
  shaShort: string;
  messageFirstLine: string;
  authorLogin: string;
  authorName: string;
  committedAt: string;
  htmlUrl: string;
}

export interface GithubTrafficDayDto {
  timestamp: string;
  count: number;
  uniques: number;
}

export interface GithubTrafficSummaryDto {
  totalViews: number;
  totalUniques: number;
  days: GithubTrafficDayDto[];
}

export interface GithubRepositoryInsightsDto {
  repository: GithubRepoSummaryDto;
  contributors: GithubContributorDto[];
  subscribers: GithubUserRefDto[];
  subscribersNote: string;
  stargazersSample: GithubUserRefDto[];
  stargazersNote: string;
  recentCommits: GithubCommitSummaryDto[];
  recentCommitAuthors: GithubUserRefDto[];
  trafficViews: GithubTrafficSummaryDto | null;
  trafficNote: string;
  warnings: string[];
}
