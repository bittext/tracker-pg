package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodAgenticBankingStatusDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticBankingSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticBankingTransactionsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticTokensRequestDto;
import com.svp.tracker.finance.service.RobinhoodAgenticBankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/credit/agentic")
@RequiredArgsConstructor
public class RobinhoodAgenticBankingController {

    private final RobinhoodAgenticBankingService bankingService;

    @GetMapping("/status")
    public RobinhoodAgenticBankingStatusDto status() {
        return bankingService.status();
    }

    @PostMapping("/tokens")
    public RobinhoodAgenticBankingStatusDto saveTokens(@RequestBody RobinhoodAgenticTokensRequestDto body) {
        return bankingService.saveTokens(body);
    }

    @DeleteMapping("/connection")
    public void disconnect() {
        bankingService.disconnect();
    }

    @PostMapping("/sync")
    public RobinhoodAgenticBankingSyncResultDto sync() {
        return bankingService.syncNow();
    }

    @GetMapping("/transactions")
    public RobinhoodAgenticBankingTransactionsDto transactions() {
        return bankingService.transactions();
    }
}
