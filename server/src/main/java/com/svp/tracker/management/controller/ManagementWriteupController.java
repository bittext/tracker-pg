package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementWriteupAttachmentDto;
import com.svp.tracker.management.dto.ManagementWriteupDto;
import com.svp.tracker.management.dto.ManagementWriteupWriteRequest;
import com.svp.tracker.management.service.ManagementWriteupService;
import com.svp.tracker.management.service.ManagementWriteupService.AttachmentFile;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/management/writeups")
@RequiredArgsConstructor
public class ManagementWriteupController {

    private final ManagementWriteupService service;

    @GetMapping
    public List<ManagementWriteupDto> list(@RequestParam int year) {
        return service.listForYear(year);
    }

    @GetMapping("/{id}")
    public ManagementWriteupDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementWriteupDto create(@Valid @RequestBody ManagementWriteupWriteRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ManagementWriteupDto update(@PathVariable long id, @Valid @RequestBody ManagementWriteupWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementWriteupAttachmentDto upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
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
        String contentType = f.contentType() == null || f.contentType().isBlank()
                ? "application/octet-stream"
                : f.contentType();
        // Markup / script types must not render inline in the browser.
        if (isForcedAttachmentContentType(contentType, f.filename())) {
            mode = "attachment";
            contentType = "application/octet-stream";
        }
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(contentType));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }

    private static boolean isForcedAttachmentContentType(String contentType, String filename) {
        String ct = contentType == null ? "" : contentType.trim().toLowerCase();
        String name = filename == null ? "" : filename.trim().toLowerCase();
        if (ct.contains("html")
                || ct.contains("javascript")
                || ct.equals("image/svg+xml")
                || ct.equals("application/xhtml+xml")
                || ct.equals("text/xml") && name.endsWith(".svg")) {
            return true;
        }
        return name.endsWith(".html")
                || name.endsWith(".htm")
                || name.endsWith(".svg")
                || name.endsWith(".js")
                || name.endsWith(".mjs");
    }
}
