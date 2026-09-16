package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceTaxDeskIncomeItemWriteDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskPageDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskPaymentWriteDto;
import com.svp.tracker.finance.dto.FinanceTaxDeskSettingsWriteDto;
import com.svp.tracker.finance.service.FinanceTaxDeskService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/finance/tax/desk")
@RequiredArgsConstructor
public class FinanceTaxDeskController {

    private final FinanceTaxDeskService service;

    @GetMapping
    public FinanceTaxDeskPageDto get(
            @RequestParam(name = "year") int year,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.load(year, asOf, true);
    }

    @PostMapping("/snapshot")
    public FinanceTaxDeskPageDto snapshot(@RequestParam(name = "year") int year) {
        return service.load(year, null, true);
    }

    @PutMapping("/settings")
    public FinanceTaxDeskPageDto saveSettings(
            @RequestParam(name = "year") int year, @RequestBody FinanceTaxDeskSettingsWriteDto body) {
        return service.saveSettings(year, body);
    }

    @PostMapping("/income")
    public FinanceTaxDeskPageDto addIncome(
            @RequestParam(name = "year") int year, @RequestBody FinanceTaxDeskIncomeItemWriteDto body) {
        return service.addIncome(year, body);
    }

    @PutMapping("/income/{id}")
    public FinanceTaxDeskPageDto updateIncome(
            @RequestParam(name = "year") int year,
            @PathVariable long id,
            @RequestBody FinanceTaxDeskIncomeItemWriteDto body) {
        return service.updateIncome(year, id, body);
    }

    @DeleteMapping("/income/{id}")
    public FinanceTaxDeskPageDto deleteIncome(@RequestParam(name = "year") int year, @PathVariable long id) {
        return service.deleteIncome(year, id);
    }

    @PostMapping("/payments")
    public FinanceTaxDeskPageDto addPayment(
            @RequestParam(name = "year") int year, @RequestBody FinanceTaxDeskPaymentWriteDto body) {
        return service.addPayment(year, body);
    }

    @DeleteMapping("/payments/{id}")
    public FinanceTaxDeskPageDto deletePayment(@RequestParam(name = "year") int year, @PathVariable long id) {
        return service.deletePayment(year, id);
    }
}
