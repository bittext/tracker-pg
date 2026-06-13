package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceInvestmentDto;
import com.svp.tracker.finance.dto.FinanceInvestmentOptionsDto;
import com.svp.tracker.finance.dto.FinanceInvestmentRequestDto;
import com.svp.tracker.finance.service.FinanceInvestmentService;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/investments")
@RequiredArgsConstructor
@Slf4j
public class FinanceInvestmentController {

    private final FinanceInvestmentService investmentService;

    @GetMapping("/options")
    public FinanceInvestmentOptionsDto options() {
        return investmentService.options();
    }

    @GetMapping
    public List<FinanceInvestmentDto> list() {
        return investmentService.listForCurrentUser();
    }

    @GetMapping("/{id}")
    public FinanceInvestmentDto get(@PathVariable long id) {
        return investmentService.getForCurrentUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceInvestmentDto create(@RequestBody FinanceInvestmentRequestDto body) {
        log.info("POST /api/finance/investments institution={} name={}", body.institution(), body.name());
        return investmentService.createForCurrentUser(body);
    }

    @PutMapping("/{id}")
    public FinanceInvestmentDto update(@PathVariable long id, @RequestBody FinanceInvestmentRequestDto body) {
        log.info("PUT /api/finance/investments/{} name={}", id, body.name());
        return investmentService.updateForCurrentUser(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/investments/{}", id);
        investmentService.deleteForCurrentUser(id);
    }
}
