package com.svp.tracker.management.controller;

import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.dto.ManagementSubscriptionDto;
import com.svp.tracker.management.dto.ManagementSubscriptionWriteRequest;
import com.svp.tracker.management.service.ManagementSubscriptionsService;
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
@RequestMapping("/api/management/subscriptions")
@RequiredArgsConstructor
public class ManagementSubscriptionsController {

    private final ManagementSubscriptionsService service;

    @GetMapping
    public List<ManagementSubscriptionDto> list(@RequestParam(defaultValue = "LIFE") String desk) {
        return service.list(ManagementDesk.fromParam(desk));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementSubscriptionDto create(
            @Valid @RequestBody ManagementSubscriptionWriteRequest body,
            @RequestParam(defaultValue = "LIFE") String desk) {
        return service.create(body, ManagementDesk.fromParam(desk));
    }

    @PutMapping("/{id}")
    public ManagementSubscriptionDto update(
            @PathVariable long id, @Valid @RequestBody ManagementSubscriptionWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
