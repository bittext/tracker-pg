package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceInsuranceOptionsDto;
import com.svp.tracker.finance.dto.FinanceInsurancePolicyDto;
import com.svp.tracker.finance.dto.FinanceInsurancePolicyRequestDto;
import com.svp.tracker.finance.dto.FinanceInsuranceSummaryDto;
import com.svp.tracker.finance.service.FinanceInsurancePolicyService;
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
@RequestMapping("/api/finance/insurance-policies")
@RequiredArgsConstructor
@Slf4j
public class FinanceInsurancePolicyController {

    private final FinanceInsurancePolicyService policyService;

    @GetMapping("/options")
    public FinanceInsuranceOptionsDto options() {
        return policyService.options();
    }

    @GetMapping("/summary")
    public FinanceInsuranceSummaryDto summary() {
        return policyService.summaryForCurrentUser();
    }

    @GetMapping
    public List<FinanceInsurancePolicyDto> list() {
        return policyService.listForCurrentUser();
    }

    @GetMapping("/{id}")
    public FinanceInsurancePolicyDto get(@PathVariable long id) {
        return policyService.getForCurrentUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceInsurancePolicyDto create(@RequestBody FinanceInsurancePolicyRequestDto body) {
        log.info("POST /api/finance/insurance-policies carrier={} type={}", body.carrier(), body.policyType());
        return policyService.createForCurrentUser(body);
    }

    @PutMapping("/{id}")
    public FinanceInsurancePolicyDto update(@PathVariable long id, @RequestBody FinanceInsurancePolicyRequestDto body) {
        log.info("PUT /api/finance/insurance-policies/{} carrier={}", id, body.carrier());
        return policyService.updateForCurrentUser(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/insurance-policies/{}", id);
        policyService.deleteForCurrentUser(id);
    }
}
