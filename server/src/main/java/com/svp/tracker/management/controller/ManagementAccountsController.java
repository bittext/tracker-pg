package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementAccountDto;
import com.svp.tracker.management.dto.ManagementAccountImportRequest;
import com.svp.tracker.management.dto.ManagementAccountImportResultDto;
import com.svp.tracker.management.dto.ManagementAccountWriteRequest;
import com.svp.tracker.management.service.ManagementAccountsService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/accounts")
@RequiredArgsConstructor
public class ManagementAccountsController {

    private final ManagementAccountsService service;

    @GetMapping
    public List<ManagementAccountDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementAccountDto create(@Valid @RequestBody ManagementAccountWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ManagementAccountDto update(
            @PathVariable long id, @Valid @RequestBody ManagementAccountWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    /** Bulk-import entries (one-time migration from browser localStorage). Returns counts; duplicates are skipped. */
    @PostMapping("/bulk-import")
    public ManagementAccountImportResultDto bulkImport(@Valid @RequestBody ManagementAccountImportRequest body) {
        return service.bulkImport(body);
    }
}
