package com.svp.tracker.admin.github;

import com.svp.tracker.admin.github.dto.GithubFeatureHistoryDto;
import com.svp.tracker.admin.github.dto.GithubRepositoryInsightsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/github")
@RequiredArgsConstructor
public class AdminGithubController {

    private final GithubRepositoryInsightsService githubRepositoryInsightsService;

    @GetMapping("/repository-insights")
    public GithubRepositoryInsightsDto repositoryInsights() {
        return githubRepositoryInsightsService.loadInsights();
    }

    @GetMapping("/feature-history")
    public GithubFeatureHistoryDto featureHistory() {
        return githubRepositoryInsightsService.loadFeatureHistory();
    }
}
