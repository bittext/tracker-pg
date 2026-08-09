package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.MarketsJourneyDto;
import com.svp.tracker.finance.dto.MarketsJourneyEntryDto;
import com.svp.tracker.finance.dto.MarketsJourneyEntryWriteRequest;
import com.svp.tracker.finance.dto.MarketsJourneyWriteRequest;
import com.svp.tracker.finance.service.MarketsJourneyService;
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
@RequestMapping({"/api/markets/journeys", "/api/finance/journeys"})
@RequiredArgsConstructor
public class MarketsJourneyController {

    private final MarketsJourneyService service;

    @GetMapping
    public List<MarketsJourneyDto> list() {
        return service.listForCurrentUser();
    }

    @GetMapping("/{id}")
    public MarketsJourneyDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarketsJourneyDto create(@RequestBody(required = false) MarketsJourneyWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public MarketsJourneyDto update(@PathVariable long id, @RequestBody MarketsJourneyWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PutMapping("/{id}/entries")
    public MarketsJourneyEntryDto upsertEntry(
            @PathVariable long id, @RequestBody MarketsJourneyEntryWriteRequest body) {
        return service.upsertEntry(id, body);
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable long id, @PathVariable long entryId) {
        service.deleteEntry(id, entryId);
    }
}
