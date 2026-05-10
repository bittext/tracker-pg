package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementWorkLogAttachmentDto;
import com.svp.tracker.management.dto.ManagementWorkLogCalendarDto;
import com.svp.tracker.management.dto.ManagementWorkLogEntryDto;
import com.svp.tracker.management.dto.ManagementWorkLogEntryWriteRequest;
import com.svp.tracker.management.service.ManagementWorkLogService;
import com.svp.tracker.management.service.ManagementWorkLogService.AttachmentFile;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/management/work-log")
@RequiredArgsConstructor
public class ManagementWorkLogController {

    private final ManagementWorkLogService service;

    @GetMapping("/entries")
    public List<ManagementWorkLogEntryDto> listBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.listBetween(from, to);
    }

    @GetMapping("/entries/day")
    public List<ManagementWorkLogEntryDto> listForDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.listForDay(date);
    }

    @GetMapping("/calendar")
    public ManagementWorkLogCalendarDto calendar(@RequestParam int year) {
        return service.calendar(year);
    }

    @GetMapping("/entries/{id}")
    public ManagementWorkLogEntryDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementWorkLogEntryDto create(@Valid @RequestBody ManagementWorkLogEntryWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/entries/{id}")
    public ManagementWorkLogEntryDto update(
            @PathVariable long id, @Valid @RequestBody ManagementWorkLogEntryWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/entries/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementWorkLogAttachmentDto upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        return service.addAttachment(id, file);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable long attachmentId) {
        service.deleteAttachment(attachmentId);
    }

    @GetMapping("/attachments/{id}/file")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable("id") long attachmentId, @RequestParam(defaultValue = "inline") String disposition) {
        AttachmentFile f = service.readAttachmentFile(attachmentId);
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }
}
