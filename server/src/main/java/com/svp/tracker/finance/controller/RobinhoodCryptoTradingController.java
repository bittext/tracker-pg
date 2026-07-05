package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodCryptoTradingCredentialsRequestDto;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingStatusDto;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeEvaluateDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeRunDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeSettingsDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeSettingsRequestDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoOrderDto;
import com.svp.tracker.finance.service.RobinhoodCryptoTradingService;
import com.svp.tracker.finance.service.RobinhoodRhCryptoAutoTradeService;
import com.svp.tracker.finance.service.RobinhoodRhCryptoOrderService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/robinhood/crypto-trading")
@RequiredArgsConstructor
public class RobinhoodCryptoTradingController {

    private final RobinhoodCryptoTradingService cryptoTradingService;
    private final RobinhoodRhCryptoOrderService cryptoOrderService;
    private final RobinhoodRhCryptoAutoTradeService cryptoAutoTradeService;

    @GetMapping("/status")
    public RobinhoodCryptoTradingStatusDto status() {
        return cryptoTradingService.status();
    }

    @PutMapping("/credentials")
    public RobinhoodCryptoTradingStatusDto saveCredentials(
            @RequestBody RobinhoodCryptoTradingCredentialsRequestDto body) {
        return cryptoTradingService.saveCredentials(body);
    }

    @DeleteMapping("/connection")
    public void disconnect() {
        cryptoTradingService.disconnect();
    }

    @PostMapping("/sync")
    public RobinhoodCryptoTradingSyncResultDto sync() {
        return cryptoTradingService.syncNow();
    }

    @GetMapping("/auto-trade/settings")
    public RobinhoodRhCryptoAutoTradeSettingsDto autoTradeSettings() {
        return cryptoOrderService.autoTradeSettings();
    }

    @PutMapping("/auto-trade/settings")
    public RobinhoodRhCryptoAutoTradeSettingsDto saveAutoTradeSettings(
            @RequestBody RobinhoodRhCryptoAutoTradeSettingsRequestDto body) {
        return cryptoOrderService.saveAutoTradeSettings(body);
    }

    @PostMapping("/auto-trade/evaluate")
    public RobinhoodRhCryptoAutoTradeEvaluateDto evaluateAutoTrade() {
        return cryptoAutoTradeService.evaluateForCurrentUser();
    }

    @GetMapping("/auto-trade/runs")
    public List<RobinhoodRhCryptoAutoTradeRunDto> autoTradeRuns() {
        return cryptoAutoTradeService.recentRuns();
    }

    @GetMapping("/orders")
    public List<RobinhoodRhCryptoOrderDto> orders() {
        return cryptoOrderService.recentOrders();
    }
}
