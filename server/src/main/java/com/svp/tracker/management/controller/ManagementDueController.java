package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementDueItemWriteRequest;
import com.svp.tracker.management.dto.ManagementDueMonthDto;
import com.svp.tracker.management.dto.ManagementDueSettleRequest;
import com.svp.tracker.management.service.ManagementDueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/management/due")
@RequiredArgsConstructor
public class ManagementDueController {

    private final ManagementDueService service;

    @GetMapping("/month")
    public ManagementDueMonthDto month(@RequestParam int year, @RequestParam int month) {
        return service.month(year, month);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementDueMonthDto create(@Valid @RequestBody ManagementDueItemWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/items/{id}")
    public ManagementDueMonthDto update(@PathVariable long id, @Valid @RequestBody ManagementDueItemWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/items/{id}")
    public ManagementDueMonthDto delete(
            @PathVariable long id, @RequestParam int year, @RequestParam int month) {
        return service.delete(id, year, month);
    }

    @PutMapping("/items/{id}/settle")
    public ManagementDueMonthDto settle(@PathVariable long id, @Valid @RequestBody ManagementDueSettleRequest body) {
        return service.settle(id, body);
    }
}
