package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.DayOneCalendarDayDto;
import com.svp.tracker.management.dto.DayOneCountsDto;
import com.svp.tracker.management.dto.ManagementDayOneAttachmentDto;
import com.svp.tracker.management.dto.ManagementDayOneEntryWriteRequest;
import com.svp.tracker.management.dto.ManagementDayOneLogDto;
import com.svp.tracker.management.dto.ManagementDayOneTagDefDto;
import com.svp.tracker.management.service.ManagementDayOneService;
import jakarta.validation.Valid;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/management/day-one")
@RequiredArgsConstructor
public class ManagementDayOneController {

    private final ManagementDayOneService dayOneService;

    @GetMapping("/tag-definitions")
    public List<ManagementDayOneTagDefDto> listTagDefinitions() {
        return dayOneService.listTagDefinitions();
    }

    @GetMapping("/entries")
    public List<ManagementDayOneLogDto> searchEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) Long ownerUserId) {
        return dayOneService.searchEntries(from, to, q, tagIds, ownerUserId);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementDayOneLogDto create(@Valid @RequestBody ManagementDayOneEntryWriteRequest body) {
        return dayOneService.create(body);
    }

    @PutMapping("/entries/{id}")
    public ManagementDayOneLogDto update(@PathVariable long id, @Valid @RequestBody ManagementDayOneEntryWriteRequest body) {
        return dayOneService.update(id, body);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        dayOneService.delete(id);
    }

    @GetMapping("/calendar")
    public List<DayOneCalendarDayDto> calendar(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long ownerUserId) {
        return dayOneService.calendar(year, month, ownerUserId);
    }

    @GetMapping("/counts")
    public DayOneCountsDto counts(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day,
            @RequestParam(required = false) Long ownerUserId) {
        return dayOneService.counts(year, month, day, ownerUserId);
    }

    @PostMapping(value = "/entries/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ManagementDayOneAttachmentDto uploadAttachment(
            @PathVariable long id, @RequestPart("file") MultipartFile file) throws IOException {
        return dayOneService.addAttachment(id, file);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable long attachmentId) throws IOException {
        dayOneService.deleteAttachment(attachmentId);
    }

    @GetMapping("/attachments/{attachmentId}/raw")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable long attachmentId) throws IOException {
        byte[] body = dayOneService.readAttachmentBytes(attachmentId);
        String ct = dayOneService.attachmentContentType(attachmentId);
        String filename = dayOneService.attachmentFilename(attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename.replace("\"", "") + "\"")
                .body(body);
    }

}
