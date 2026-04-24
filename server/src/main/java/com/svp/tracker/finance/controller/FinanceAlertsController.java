package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceAlertEvaluationDto;
import com.svp.tracker.finance.dto.FinanceAlertEventDto;
import com.svp.tracker.finance.dto.FinanceStockAlertDto;
import com.svp.tracker.finance.dto.FinanceStockAlertRequestDto;
import com.svp.tracker.finance.service.FinanceAlertEvaluationService;
import com.svp.tracker.finance.service.FinanceStockAlertService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/robinhood/alerts")
@RequiredArgsConstructor
@Slf4j
public class FinanceAlertsController {

    private final FinanceStockAlertService alertService;
    private final FinanceAlertEvaluationService evaluationService;

    @GetMapping
    public List<FinanceStockAlertDto> listAlerts() {
        return alertService.listCurrentUserAlerts();
    }

    @PostMapping
    public FinanceStockAlertDto createAlert(@RequestBody FinanceStockAlertRequestDto req) {
        log.info("POST /api/finance/robinhood/alerts symbol={} trigger={}", req.symbol(), req.triggerType());
        return alertService.createCurrentUserAlert(req);
    }

    @PutMapping("/{id}")
    public FinanceStockAlertDto updateAlert(@PathVariable long id, @RequestBody FinanceStockAlertRequestDto req) {
        log.info("PUT /api/finance/robinhood/alerts/{} symbol={} trigger={}", id, req.symbol(), req.triggerType());
        return alertService.updateCurrentUserAlert(id, req);
    }

    @DeleteMapping("/{id}")
    public void deleteAlert(@PathVariable long id) {
        log.info("DELETE /api/finance/robinhood/alerts/{}", id);
        alertService.deleteCurrentUserAlert(id);
    }

    @PostMapping("/evaluate")
    public FinanceAlertEvaluationDto evaluateAlerts() {
        return evaluationService.evaluateCurrentUserAlerts();
    }

    @GetMapping("/events")
    public List<FinanceAlertEventDto> listEvents(@RequestParam(name = "limit", required = false) Integer limit) {
        return alertService.listCurrentUserEvents(limit);
    }
}
