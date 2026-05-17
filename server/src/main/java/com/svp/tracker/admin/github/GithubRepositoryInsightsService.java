package com.svp.tracker.admin.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.admin.github.dto.GithubCommitSummaryDto;
import com.svp.tracker.admin.github.dto.GithubContributorDto;
import com.svp.tracker.admin.github.dto.GithubFeatureHistoryDto;
import com.svp.tracker.admin.github.dto.GithubFeatureHistoryEntryDto;
import com.svp.tracker.admin.github.dto.GithubRepoSummaryDto;
import com.svp.tracker.admin.github.dto.GithubRepositoryInsightsDto;
import com.svp.tracker.admin.github.dto.GithubTrafficDayDto;
import com.svp.tracker.admin.github.dto.GithubTrafficSummaryDto;
import com.svp.tracker.admin.github.dto.GithubUserRefDto;
import com.svp.tracker.config.GithubProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubRepositoryInsightsService {

    private static final String BASE = "https://api.github.com";
    private static final int FEATURE_HISTORY_PER_PAGE = 100;
    /** Up to 10_000 raw commits from GitHub (100 pages × 100). */
    private static final int FEATURE_HISTORY_MAX_PAGES = 100;

    private final GithubProperties githubProperties;
    private final ClientHttpRequestFactory outboundHttpRequestFactory;

    /** Local mapper: Spring Boot 4 may not register an {@link ObjectMapper} bean; GitHub JSON parsing only. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GithubRepositoryInsightsDto loadInsights() {
        if (!githubProperties.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub is disabled or owner/repo is not set. Configure tracker.github.enabled, owner, and repo (optional api-token for higher limits, subscribers list, and traffic).");
        }
        String owner = githubProperties.owner();
        String repo = githubProperties.repo();
        String basePath = "/repos/" + owner + "/" + repo;
        List<String> warnings = new ArrayList<>();

        JsonNode repoJson = getJson(basePath, warnings)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Could not read repository from GitHub. Check owner/repo and network; use api-token if the repo is private."));

        GithubRepoSummaryDto summary = parseRepo(repoJson);

        List<GithubContributorDto> contributors = parseContributors(getJson(basePath + "/contributors?per_page=100", warnings));

        List<GithubUserRefDto> subscribers = new ArrayList<>();
        String subscribersNote =
                "Accounts watching this repository for GitHub notifications. Listing often requires a token with access to the repo.";
        parseUserArray(getJson(basePath + "/subscribers?per_page=100", warnings)).ifPresent(subscribers::addAll);

        List<GithubUserRefDto> stargazers = new ArrayList<>();
        parseUserArray(getJson(basePath + "/stargazers?per_page=50", warnings)).ifPresent(stargazers::addAll);
        String stargazersNote =
                "Users who starred the repository (REST API returns an early page in chronological order, not “most recent” without GitHub GraphQL).";

        List<GithubCommitSummaryDto> commits = parseCommits(getJson(basePath + "/commits?per_page=30", warnings));
        List<GithubUserRefDto> recentAuthors = uniqueCommitAuthors(commits);

        GithubTrafficSummaryDto traffic = null;
        String trafficNote;
        Optional<JsonNode> trafficJson = getJson(basePath + "/traffic/views", warnings);
        if (trafficJson.isPresent()) {
            traffic = parseTraffic(trafficJson.get());
            trafficNote = "Aggregate views/clones for the last ~14 days (requires a token with push access). GitHub does not expose individual “who opened the repo” identities via this API.";
        } else {
            trafficNote =
                    "Traffic (unique visitors over time) requires a personal access token with push (or admin) permission on this repository. Anonymous page views are not listed per-user.";
        }

        return new GithubRepositoryInsightsDto(
                summary,
                contributors,
                subscribers,
                subscribersNote,
                stargazers,
                stargazersNote,
                commits,
                recentAuthors,
                traffic,
                trafficNote,
                warnings);
    }

    /** Full default-branch commit history (paginated), with humanized summaries for Admin → Features. */
    public GithubFeatureHistoryDto loadFeatureHistory() {
        if (!githubProperties.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub is disabled or owner/repo is not set. Configure tracker.github.enabled, owner, and repo (optional api-token for higher limits).");
        }
        String owner = githubProperties.owner();
        String repo = githubProperties.repo();
        String basePath = "/repos/" + owner + "/" + repo;
        List<String> warnings = new ArrayList<>();

        JsonNode repoJson = getJson(basePath, warnings)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Could not read repository from GitHub. Check owner/repo and network; use api-token if the repo is private."));

        GithubRepoSummaryDto summary = parseRepo(repoJson);
        CommitHistoryFetch fetch = fetchCommitHistory(basePath, warnings);
        List<GithubCommitSummaryDto> raw = fetch.commits();

        List<GithubFeatureHistoryEntryDto> entries = new ArrayList<>();
        int mergesOmitted = 0;
        for (GithubCommitSummaryDto c : raw) {
            if (isMergeCommitMessage(c.messageFirstLine())) {
                mergesOmitted++;
                continue;
            }
            entries.add(new GithubFeatureHistoryEntryDto(
                    c.shaShort(),
                    toFeatureSummary(c.messageFirstLine()),
                    c.messageFirstLine(),
                    c.messageFull(),
                    c.authorLogin(),
                    c.authorName(),
                    c.committedAt(),
                    c.htmlUrl()));
        }

        String sourceNote =
                "Commits on the default branch (newest first), loaded from GitHub in pages of "
                        + FEATURE_HISTORY_PER_PAGE
                        + ". Merge commits are hidden; subject lines are cleaned for readability.";
        if (fetch.moreAvailable()) {
            sourceNote +=
                    " History was capped at "
                            + (FEATURE_HISTORY_MAX_PAGES * FEATURE_HISTORY_PER_PAGE)
                            + " commits — older commits may still exist on GitHub.";
        }
        return new GithubFeatureHistoryDto(
                summary,
                entries,
                raw.size(),
                mergesOmitted,
                fetch.moreAvailable(),
                sourceNote,
                warnings);
    }

    private record CommitHistoryFetch(List<GithubCommitSummaryDto> commits, boolean moreAvailable) {}

    private CommitHistoryFetch fetchCommitHistory(String basePath, List<String> warnings) {
        List<GithubCommitSummaryDto> all = new ArrayList<>();
        boolean moreAvailable = false;
        for (int page = 1; page <= FEATURE_HISTORY_MAX_PAGES; page++) {
            String path =
                    basePath + "/commits?per_page=" + FEATURE_HISTORY_PER_PAGE + "&page=" + page;
            List<GithubCommitSummaryDto> batch = parseCommits(getJson(path, warnings));
            if (batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < FEATURE_HISTORY_PER_PAGE) {
                break;
            }
            if (page == FEATURE_HISTORY_MAX_PAGES) {
                moreAvailable = true;
            }
        }
        return new CommitHistoryFetch(all, moreAvailable);
    }

    private static boolean isMergeCommitMessage(String firstLine) {
        if (firstLine == null || firstLine.isBlank()) {
            return false;
        }
        String lower = firstLine.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("merge pull request")
                || lower.startsWith("merge branch")
                || lower.startsWith("merge remote-tracking")
                || lower.equals("merge");
    }

    static String toFeatureSummary(String firstLine) {
        if (firstLine == null || firstLine.isBlank()) {
            return "Update";
        }
        String s = firstLine.strip();
        s = s.replaceFirst(
                "^(?i)(feat|fix|chore|docs|style|refactor|perf|test|build|ci|revert)(\\([^)]+\\))?!?:\\s*",
                "");
        s = s.replaceFirst("^(?i)(release|merge)\\s*:\\s*", "");
        s = s.strip();
        if (s.isEmpty()) {
            s = firstLine.strip();
        }
        if (!s.isEmpty()) {
            s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
        return s.isEmpty() ? "Update" : s;
    }

    private Optional<JsonNode> getJson(String path, List<String> warnings) {
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(outboundHttpRequestFactory)
                    .baseUrl(BASE)
                    .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .defaultHeader(HttpHeaders.USER_AGENT, "TrackerPgServer/7.2.0")
                    .build();
            // Jackson 3 + RestClient: cannot deserialize directly into abstract JsonNode.class — read String then parse.
            String raw = client.get()
                    .uri(path)
                    .headers(this::applyAuth)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(raw);
            return Optional.of(body);
        } catch (JsonProcessingException e) {
            log.warn("GitHub JSON parse failed: {}", path, e);
            warnings.add("GitHub " + path + ": invalid JSON response");
            return Optional.empty();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 404 || code == 403 || code == 401) {
                warnings.add("GitHub " + path + " → HTTP " + code);
                return Optional.empty();
            }
            log.warn("GitHub API error {} {}", path, e.getStatusCode());
            warnings.add("GitHub " + path + " → HTTP " + code + ": " + safeMsg(e));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("GitHub API request failed: {}", path, e);
            warnings.add("GitHub " + path + ": " + safeMsg(e));
            return Optional.empty();
        }
    }

    private void applyAuth(HttpHeaders h) {
        if (!StringUtils.hasText(githubProperties.apiToken())) {
            return;
        }
        String t = githubProperties.apiToken();
        if (t.startsWith("ghp_")) {
            h.add(HttpHeaders.AUTHORIZATION, "token " + t);
        } else {
            h.add(HttpHeaders.AUTHORIZATION, "Bearer " + t);
        }
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m.replace('\n', ' ');
    }

    private static GithubRepoSummaryDto parseRepo(JsonNode n) {
        return new GithubRepoSummaryDto(
                text(n, "full_name"),
                text(n, "html_url"),
                text(n, "description"),
                text(n, "default_branch"),
                text(n, "pushed_at"),
                longVal(n, "stargazers_count"),
                longVal(n, "forks_count"),
                longVal(n, "subscribers_count"),
                longVal(n, "watchers_count"),
                longVal(n, "open_issues_count"));
    }

    private static long longVal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || !v.isNumber() ? 0L : v.asLong();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        return v.asText("");
    }

    private static List<GithubContributorDto> parseContributors(Optional<JsonNode> root) {
        List<GithubContributorDto> out = new ArrayList<>();
        if (root.isEmpty() || !root.get().isArray()) {
            return out;
        }
        for (JsonNode n : root.get()) {
            String login = text(n, "login");
            if (login.isEmpty()) {
                continue;
            }
            out.add(new GithubContributorDto(
                    login, text(n, "html_url"), text(n, "avatar_url"), n.path("contributions").asInt(0)));
        }
        return out;
    }

    private static Optional<List<GithubUserRefDto>> parseUserArray(Optional<JsonNode> root) {
        if (root.isEmpty() || !root.get().isArray()) {
            return Optional.empty();
        }
        List<GithubUserRefDto> out = new ArrayList<>();
        for (JsonNode n : root.get()) {
            String login = text(n, "login");
            if (login.isEmpty()) {
                continue;
            }
            out.add(new GithubUserRefDto(login, text(n, "html_url"), text(n, "avatar_url")));
        }
        return Optional.of(out);
    }

    private static List<GithubCommitSummaryDto> parseCommits(Optional<JsonNode> root) {
        List<GithubCommitSummaryDto> out = new ArrayList<>();
        if (root.isEmpty() || !root.get().isArray()) {
            return out;
        }
        for (JsonNode n : root.get()) {
            String sha = text(n, "sha");
            String shaShort = sha.length() > 7 ? sha.substring(0, 7) : sha;
            JsonNode commit = n.path("commit");
            String msg = commit.path("message").asText("");
            String first = msg.contains("\n") ? msg.substring(0, msg.indexOf('\n')) : msg;
            if (first.length() > 200) {
                first = first.substring(0, 197) + "...";
            }
            JsonNode author = commit.path("author");
            String name = text(author, "name");
            String date = text(author, "date");
            String login = "";
            JsonNode ghAuthor = n.path("author");
            if (!ghAuthor.isMissingNode() && !ghAuthor.isNull()) {
                login = text(ghAuthor, "login");
            }
            out.add(new GithubCommitSummaryDto(
                    shaShort, first, msg, login, name, date, text(n, "html_url")));
        }
        return out;
    }

    private static List<GithubUserRefDto> uniqueCommitAuthors(List<GithubCommitSummaryDto> commits) {
        Map<String, GithubUserRefDto> byLogin = new LinkedHashMap<>();
        for (GithubCommitSummaryDto c : commits) {
            if (c.authorLogin() != null && !c.authorLogin().isBlank()) {
                String login = c.authorLogin().trim();
                byLogin.putIfAbsent(
                        login.toLowerCase(Locale.ROOT),
                        new GithubUserRefDto(login, "https://github.com/" + login, ""));
            }
        }
        return new ArrayList<>(byLogin.values());
    }

    private static GithubTrafficSummaryDto parseTraffic(JsonNode n) {
        int total = n.path("count").asInt(0);
        int uniques = n.path("uniques").asInt(0);
        List<GithubTrafficDayDto> days = new ArrayList<>();
        JsonNode views = n.path("views");
        if (views.isArray()) {
            for (JsonNode d : views) {
                days.add(new GithubTrafficDayDto(
                        text(d, "timestamp"), d.path("count").asInt(0), d.path("uniques").asInt(0)));
            }
        }
        return new GithubTrafficSummaryDto(total, uniques, days);
    }
}
