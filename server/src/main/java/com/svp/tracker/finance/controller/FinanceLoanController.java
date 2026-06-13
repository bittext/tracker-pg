package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceLoanDto;
import com.svp.tracker.finance.dto.FinanceLoanOptionsDto;
import com.svp.tracker.finance.dto.FinanceLoanRequestDto;
import com.svp.tracker.finance.service.FinanceLoanService;
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
@RequestMapping("/api/finance/loans")
@RequiredArgsConstructor
@Slf4j
public class FinanceLoanController {

    private final FinanceLoanService loanService;

    @GetMapping("/options")
    public FinanceLoanOptionsDto options() {
        return loanService.options();
    }

    @GetMapping
    public List<FinanceLoanDto> list() {
        return loanService.listForCurrentUser();
    }

    @GetMapping("/{id}")
    public FinanceLoanDto get(@PathVariable long id) {
        return loanService.getForCurrentUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceLoanDto create(@RequestBody FinanceLoanRequestDto body) {
        log.info("POST /api/finance/loans institution={} nature={}", body.institution(), body.loanNature());
        return loanService.createForCurrentUser(body);
    }

    @PutMapping("/{id}")
    public FinanceLoanDto update(@PathVariable long id, @RequestBody FinanceLoanRequestDto body) {
        log.info("PUT /api/finance/loans/{} institution={}", id, body.institution());
        return loanService.updateForCurrentUser(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/loans/{}", id);
        loanService.deleteForCurrentUser(id);
    }
}
