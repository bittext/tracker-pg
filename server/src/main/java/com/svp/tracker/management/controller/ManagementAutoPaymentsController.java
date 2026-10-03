package com.svp.tracker.management.controller;

import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.dto.ManagementAutoPaymentDto;
import com.svp.tracker.management.dto.ManagementAutoPaymentWriteRequest;
import com.svp.tracker.management.service.ManagementAutoPaymentsService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/management/auto-payments")
@RequiredArgsConstructor
public class ManagementAutoPaymentsController {

    private final ManagementAutoPaymentsService service;

    @GetMapping
    public List<ManagementAutoPaymentDto> list(@RequestParam(defaultValue = "LIFE") String desk) {
        return service.list(ManagementDesk.fromParam(desk));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementAutoPaymentDto create(
            @Valid @RequestBody ManagementAutoPaymentWriteRequest body,
            @RequestParam(defaultValue = "LIFE") String desk) {
        return service.create(body, ManagementDesk.fromParam(desk));
    }

    @PutMapping("/{id}")
    public ManagementAutoPaymentDto update(
            @PathVariable long id, @Valid @RequestBody ManagementAutoPaymentWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
