package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodCryptoTradingCredentialsRequestDto;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingStatusDto;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingSyncResultDto;
import com.svp.tracker.finance.service.RobinhoodCryptoTradingService;
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
}
