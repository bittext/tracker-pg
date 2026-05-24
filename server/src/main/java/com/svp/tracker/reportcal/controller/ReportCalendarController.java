package com.svp.tracker.reportcal.controller;

import com.svp.tracker.reportcal.dto.ReportCalendarAttachmentDto;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryDto;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryWriteDto;
import com.svp.tracker.reportcal.service.ReportCalendarService;
import com.svp.tracker.reportcal.service.ReportCalendarService.AttachmentFile;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/report-calendar")
@RequiredArgsConstructor
public class ReportCalendarController {

    private final ReportCalendarService service;

    @GetMapping("/entries")
    public List<ReportCalendarEntryDto> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String calendarType) {
        return service.listInRange(from, to, calendarType);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCalendarEntryDto create(@Valid @RequestBody ReportCalendarEntryWriteDto body) {
        return service.create(body);
    }

    @PutMapping("/entries/{id}")
    public ReportCalendarEntryDto update(
            @PathVariable long id, @Valid @RequestBody ReportCalendarEntryWriteDto body) {
        return service.update(id, body);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/entries/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCalendarAttachmentDto upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
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
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, mode + "; filename=\"" + f.filename() + "\"")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(f.body());
    }
}
