package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceCreditStandingDto;
import com.svp.tracker.finance.dto.FinanceCreditStandingRequestDto;
import com.svp.tracker.finance.service.FinanceCreditStandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/credit-standing")
@RequiredArgsConstructor
public class FinanceCreditStandingController {

    private final FinanceCreditStandingService service;

    @GetMapping
    public FinanceCreditStandingDto get() {
        return service.getForCurrentUser();
    }

    @PutMapping
    public FinanceCreditStandingDto upsert(@RequestBody FinanceCreditStandingRequestDto body) {
        return service.upsertForCurrentUser(body);
    }
}
