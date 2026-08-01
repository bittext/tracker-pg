package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryDashboardDto;
import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryWatchRequestDto;
import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryWatchResultDto;
import com.svp.tracker.finance.service.AiTokenFactoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/markets/ai-token-factory", "/api/finance/ai-token-factory"})
@RequiredArgsConstructor
public class AiTokenFactoryController {

    private final AiTokenFactoryService service;

    @GetMapping
    public AiTokenFactoryDashboardDto dashboard() {
        return service.dashboard();
    }

    @PostMapping("/watch")
    @ResponseStatus(HttpStatus.OK)
    public AiTokenFactoryWatchResultDto watch(@RequestBody AiTokenFactoryWatchRequestDto body) {
        return service.addToWatch(body);
    }
}
