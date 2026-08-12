package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.MarketsRoadmapSlapPointsDto;
import com.svp.tracker.finance.service.MarketsRoadmapSlapPointsService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/markets/roadmap", "/api/finance/roadmap"})
@RequiredArgsConstructor
public class MarketsRoadmapSlapPointsController {

    private final MarketsRoadmapSlapPointsService slapPointsService;

    @GetMapping("/slap-points")
    public MarketsRoadmapSlapPointsDto slapPoints(
            @RequestParam(required = false, defaultValue = "3370") String accountSuffix,
            @RequestParam(required = false) BigDecimal stepAmount) {
        return slapPointsService.slapPoints(accountSuffix, stepAmount);
    }
}
