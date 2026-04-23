package com.svp.tracker.reportcal.controller;

import com.svp.tracker.reportcal.domain.ReportCalendarType;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryDto;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryWriteDto;
import com.svp.tracker.reportcal.service.ReportCalendarService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/report-calendar/entries")
@RequiredArgsConstructor
public class ReportCalendarController {

    private final ReportCalendarService service;

    @GetMapping
    public List<ReportCalendarEntryDto> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ReportCalendarType calendarType) {
        return service.listInRange(from, to, calendarType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCalendarEntryDto create(@Valid @RequestBody ReportCalendarEntryWriteDto body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ReportCalendarEntryDto update(
            @PathVariable long id, @Valid @RequestBody ReportCalendarEntryWriteDto body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
