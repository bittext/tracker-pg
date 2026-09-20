package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceNewsScanDto;
import com.svp.tracker.finance.dto.FinanceNewsScanTickersRequestDto;
import com.svp.tracker.finance.service.FinanceNewsScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/markets/news-scan", "/api/finance/robinhood/news-scan"})
@RequiredArgsConstructor
@Slf4j
public class FinanceNewsScanController {

    private final FinanceNewsScanService newsScanService;

    @GetMapping
    public FinanceNewsScanDto scan(@RequestParam(name = "force", required = false) Boolean force) {
        log.info("GET /api/markets/news-scan force={}", force);
        try {
            return newsScanService.scanCurrentUser(Boolean.TRUE.equals(force));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    @PutMapping("/tickers")
    public FinanceNewsScanDto replaceTickers(@RequestBody(required = false) FinanceNewsScanTickersRequestDto req) {
        log.info("PUT /api/markets/news-scan/tickers");
        return newsScanService.replaceCurrentUserTickers(req);
    }
}
