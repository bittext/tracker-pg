package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodAgenticPositionsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticStatusDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticTokensRequestDto;
import com.svp.tracker.finance.service.RobinhoodAgenticService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/robinhood/agentic")
@RequiredArgsConstructor
public class RobinhoodAgenticController {

    private final RobinhoodAgenticService agenticService;

    @GetMapping("/status")
    public RobinhoodAgenticStatusDto status() {
        return agenticService.status();
    }

    @PostMapping("/tokens")
    public RobinhoodAgenticStatusDto saveTokens(@RequestBody RobinhoodAgenticTokensRequestDto body) {
        return agenticService.saveTokens(body);
    }

    @DeleteMapping("/connection")
    public void disconnect() {
        agenticService.disconnect();
    }

    @PostMapping("/sync")
    public RobinhoodAgenticSyncResultDto sync() {
        return agenticService.syncNow();
    }

    @GetMapping("/positions")
    public RobinhoodAgenticPositionsDto positions() {
        return agenticService.positions();
    }
}
