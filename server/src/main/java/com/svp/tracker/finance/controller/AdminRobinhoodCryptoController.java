package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeEvaluateDto;
import com.svp.tracker.finance.service.RobinhoodRhCryptoAutoTradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/finance/crypto-trading")
@RequiredArgsConstructor
@Slf4j
public class AdminRobinhoodCryptoController {

    private final RobinhoodRhCryptoAutoTradeService cryptoAutoTradeService;

    @PostMapping("/actions/evaluate/{userId}")
    public RobinhoodRhCryptoAutoTradeEvaluateDto evaluateUser(@PathVariable long userId) {
        log.info("Admin manual: crypto auto-trade evaluate for user {}", userId);
        return cryptoAutoTradeService.evaluateForUser(userId, false);
    }
}
