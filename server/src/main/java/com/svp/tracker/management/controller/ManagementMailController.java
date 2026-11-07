package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementMailAccountDto;
import com.svp.tracker.management.dto.ManagementMailAccountWriteRequest;
import com.svp.tracker.management.dto.ManagementMailDigestDto;
import com.svp.tracker.management.dto.ManagementMailTestDto;
import com.svp.tracker.management.service.ManagementMailService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/mail")
@RequiredArgsConstructor
public class ManagementMailController {

    private final ManagementMailService service;

    @GetMapping("/accounts")
    public List<ManagementMailAccountDto> accounts() {
        return service.listAccounts();
    }

    @PostMapping("/accounts")
    public ManagementMailAccountDto saveAccount(@Valid @RequestBody ManagementMailAccountWriteRequest body) {
        return service.saveAccount(body);
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable long id) {
        service.deleteAccount(id);
    }

    @PostMapping("/accounts/{id}/test")
    public ManagementMailTestDto testAccount(@PathVariable long id) {
        return service.testAccount(id);
    }

    @GetMapping("/digests")
    public List<ManagementMailDigestDto> digests() {
        return service.listDigests();
    }

    @GetMapping("/digests/day")
    public List<ManagementMailDigestDto> day(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return service.day(on);
    }

    @GetMapping("/digests/{id}")
    public ManagementMailDigestDto digest(@PathVariable long id) {
        return service.digest(id);
    }

    @DeleteMapping("/digests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDigest(@PathVariable long id) {
        service.deleteDigest(id);
    }

    @PostMapping("/digests/run")
    public ManagementMailDigestDto run(@RequestParam(required = false) String slot) {
        return service.runCurrent(slot);
    }
}
