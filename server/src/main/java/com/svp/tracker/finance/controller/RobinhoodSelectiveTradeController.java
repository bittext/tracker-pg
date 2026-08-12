package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RhDailyTrackerAiInsightStatusDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeAiInsightDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeCalendarDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeEntryDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeLedgerDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeRequestDto;
import com.svp.tracker.finance.service.RobinhoodSelectiveTradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/finance/robinhood/selective-trades", "/api/markets/selective-trades"})
@RequiredArgsConstructor
@Slf4j
public class RobinhoodSelectiveTradeController {

    private final RobinhoodSelectiveTradeService selectiveTradeService;

    @GetMapping
    public RobinhoodSelectiveTradeLedgerDto ledger(
            @RequestParam int year, @RequestParam(required = false) Integer month) {
        return selectiveTradeService.ledger(year, month);
    }

    @GetMapping("/calendar")
    public RobinhoodSelectiveTradeCalendarDto calendar(
            @RequestParam int year, @RequestParam(required = false) Integer month) {
        return selectiveTradeService.calendar(year, month);
    }

    @GetMapping("/ai-status")
    public RhDailyTrackerAiInsightStatusDto aiStatus() {
        return selectiveTradeService.aiStatus();
    }

    @PostMapping("/ai-analyze")
    public RobinhoodSelectiveTradeAiInsightDto aiAnalyze(
            @RequestParam int year, @RequestParam(required = false) Integer month) {
        log.info("POST /api/finance/robinhood/selective-trades/ai-analyze year={} month={}", year, month);
        return selectiveTradeService.analyze(year, month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RobinhoodSelectiveTradeEntryDto create(@RequestBody RobinhoodSelectiveTradeRequestDto body) {
        log.info(
                "POST /api/finance/robinhood/selective-trades date={} outcome={}",
                body != null ? body.activityDate() : null,
                body != null ? body.outcome() : null);
        return selectiveTradeService.create(body);
    }

    @PutMapping("/{id}")
    public RobinhoodSelectiveTradeEntryDto update(
            @PathVariable long id, @RequestBody RobinhoodSelectiveTradeRequestDto body) {
        log.info("PUT /api/finance/robinhood/selective-trades/{}", id);
        return selectiveTradeService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/robinhood/selective-trades/{}", id);
        selectiveTradeService.delete(id);
    }
}
