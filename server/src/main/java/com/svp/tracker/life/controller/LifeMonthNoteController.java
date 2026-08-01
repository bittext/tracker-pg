package com.svp.tracker.life.controller;

import com.svp.tracker.life.dto.LifeMonthNoteAttachmentDto;
import com.svp.tracker.life.dto.LifeMonthNoteCalendarDto;
import com.svp.tracker.life.dto.LifeMonthNoteDto;
import com.svp.tracker.life.dto.LifeMonthNoteWriteRequest;
import com.svp.tracker.life.service.LifeMonthNoteService;
import com.svp.tracker.life.service.LifeMonthNoteService.AttachmentFile;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
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
@RequestMapping("/api/life/notes")
@RequiredArgsConstructor
public class LifeMonthNoteController {

    private final LifeMonthNoteService service;

    @GetMapping("/calendar")
    public LifeMonthNoteCalendarDto calendar(@RequestParam int year) {
        return service.calendar(year);
    }

    @GetMapping
    public List<LifeMonthNoteDto> list(
            @RequestParam int year, @RequestParam(required = false) @Nullable Integer month) {
        return service.list(year, month);
    }

    @GetMapping("/{id}")
    public LifeMonthNoteDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LifeMonthNoteDto create(@Valid @RequestBody LifeMonthNoteWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public LifeMonthNoteDto update(@PathVariable long id, @Valid @RequestBody LifeMonthNoteWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public LifeMonthNoteAttachmentDto upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
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
