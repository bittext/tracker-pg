package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.InvestmentThenNowRequestDto;
import com.svp.tracker.finance.dto.InvestmentThenNowResultDto;
import com.svp.tracker.finance.service.InvestmentThenNowService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Markets Research → Then & now: "$X invested on date in SYMBOL — worth now?" with optional saved answers.
 */
@RestController
@RequestMapping({"/api/markets/investment-then-now", "/api/finance/robinhood/investment-then-now"})
@RequiredArgsConstructor
@Slf4j
public class InvestmentThenNowController {

    private final InvestmentThenNowService service;

    @GetMapping
    public List<InvestmentThenNowResultDto> list() {
        return service.listSaved();
    }

    @GetMapping("/{id}")
    public InvestmentThenNowResultDto get(@PathVariable long id) {
        return service.getSaved(id);
    }

    @PostMapping("/compute")
    public InvestmentThenNowResultDto compute(@RequestBody(required = false) InvestmentThenNowRequestDto body) {
        String symbol = body == null ? null : body.symbol();
        boolean save = body != null && Boolean.TRUE.equals(body.save());
        log.info("POST investment-then-now/compute symbol={} save={}", symbol, save);
        return service.compute(body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE investment-then-now/{}", id);
        service.deleteSaved(id);
    }
}
