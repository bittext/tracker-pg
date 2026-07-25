package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.CompanyEarningsCalendarDto;
import com.svp.tracker.finance.dto.CompanyResearchCardDto;
import com.svp.tracker.finance.dto.CompanyResearchDetailDto;
import com.svp.tracker.finance.dto.CompanyResearchListDto;
import com.svp.tracker.finance.dto.CompanyResearchNoteDto;
import com.svp.tracker.finance.dto.CompanyResearchNoteRequestDto;
import com.svp.tracker.finance.dto.CompanyResearchUpdateRequestDto;
import com.svp.tracker.finance.dto.CompanyResearchUpsertRequestDto;
import com.svp.tracker.finance.service.CompanyResearchService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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

/**
 * Markets Research → Watch: earnings calendar, company cards, notes, and search.
 */
@RestController
@RequestMapping({"/api/markets/company-research", "/api/finance/robinhood/company-research"})
@RequiredArgsConstructor
@Slf4j
public class CompanyResearchController {

    private final CompanyResearchService companyResearchService;

    @GetMapping("/earnings-calendar")
    public CompanyEarningsCalendarDto earningsCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long minMarketCap) {
        log.info("GET company-research/earnings-calendar from={} days={} minMarketCap={}", from, days, minMarketCap);
        return companyResearchService.earningsCalendar(from, days, minMarketCap);
    }

    @GetMapping
    public CompanyResearchListDto list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer earningsWithinDays) {
        return companyResearchService.list(q, status, earningsWithinDays);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResearchCardDto upsert(@RequestBody CompanyResearchUpsertRequestDto body) {
        log.info("POST company-research symbol={}", body != null ? body.symbol() : null);
        return companyResearchService.upsert(body);
    }

    @GetMapping("/{symbol}")
    public CompanyResearchDetailDto detail(
            @PathVariable String symbol, @RequestParam(required = false, defaultValue = "true") boolean includeNews) {
        return companyResearchService.detail(symbol, includeNews);
    }

    @PutMapping("/{symbol}")
    public CompanyResearchCardDto update(
            @PathVariable String symbol, @RequestBody CompanyResearchUpdateRequestDto body) {
        log.info("PUT company-research/{}", symbol);
        return companyResearchService.update(symbol, body);
    }

    @DeleteMapping("/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String symbol) {
        log.info("DELETE company-research/{}", symbol);
        companyResearchService.delete(symbol);
    }

    @GetMapping("/{symbol}/notes")
    public List<CompanyResearchNoteDto> listNotes(@PathVariable String symbol) {
        return companyResearchService.listNotes(symbol);
    }

    @PostMapping("/{symbol}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResearchNoteDto addNote(
            @PathVariable String symbol, @RequestBody CompanyResearchNoteRequestDto body) {
        return companyResearchService.addNote(symbol, body);
    }

    @PutMapping("/notes/{noteId}")
    public CompanyResearchNoteDto updateNote(
            @PathVariable long noteId, @RequestBody CompanyResearchNoteRequestDto body) {
        return companyResearchService.updateNote(noteId, body);
    }

    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable long noteId) {
        companyResearchService.deleteNote(noteId);
    }
}
