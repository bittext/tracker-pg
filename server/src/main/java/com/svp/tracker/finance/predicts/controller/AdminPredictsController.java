package com.svp.tracker.finance.predicts.controller;

import com.svp.tracker.finance.predicts.dto.PredictsSourceHealthDto;
import com.svp.tracker.finance.predicts.dto.admin.PredictsActionResultDto;
import com.svp.tracker.finance.predicts.dto.admin.PredictsAdminStatsDto;
import com.svp.tracker.finance.predicts.dto.admin.PredictsConfigDto;
import com.svp.tracker.finance.predicts.service.AdminPredictsService;
import com.svp.tracker.finance.predicts.service.PredictsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin surface for Finance → Predicts. Mounted under {@code /api/admin/finance/predicts/**} so it
 * inherits the {@code hasRole("ADMIN")} rule from {@code SecurityConfig}. Read endpoints (config,
 * stats, sources) return snapshots; the {@code /actions/*} endpoints invoke the underlying schedule
 * jobs synchronously.
 */
@RestController
@RequestMapping("/api/admin/finance/predicts")
@RequiredArgsConstructor
@Slf4j
public class AdminPredictsController {

    private final AdminPredictsService adminService;
    private final PredictsService predictsService;

    @GetMapping("/config")
    public PredictsConfigDto config() {
        return adminService.config();
    }

    @GetMapping("/stats")
    public PredictsAdminStatsDto stats() {
        return adminService.stats();
    }

    @GetMapping("/sources")
    public List<PredictsSourceHealthDto> sources() {
        return predictsService.listSourceHealth();
    }

    @PostMapping("/actions/poll-stocktwits")
    public PredictsActionResultDto pollStocktwits() {
        log.info("Admin manual: poll-stocktwits");
        return adminService.runStocktwitsPoll();
    }

    @PostMapping("/actions/poll-reddit")
    public PredictsActionResultDto pollReddit() {
        log.info("Admin manual: poll-reddit");
        return adminService.runRedditPoll();
    }

    @PostMapping("/actions/recompute-baselines")
    public PredictsActionResultDto recomputeBaselines() {
        log.info("Admin manual: recompute-baselines");
        return adminService.runRecomputeBaselines();
    }

    @PostMapping("/actions/purge-mentions")
    public PredictsActionResultDto purgeMentions() {
        log.info("Admin manual: purge-mentions");
        return adminService.runPurgeMentions();
    }

    @PostMapping("/actions/auto-seed")
    public PredictsActionResultDto autoSeed() {
        log.info("Admin manual: auto-seed");
        return adminService.runAutoSeed();
    }
}
