package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementDocumentDto;
import com.svp.tracker.management.dto.ManagementDocumentWriteRequest;
import com.svp.tracker.management.service.ManagementDocumentsService;
import com.svp.tracker.management.service.ManagementDocumentsService.DocumentFile;
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
@RequestMapping("/api/management/documents")
@RequiredArgsConstructor
public class ManagementDocumentsController {

    private final ManagementDocumentsService service;

    @GetMapping
    public List<ManagementDocumentDto> list() {
        return service.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementDocumentDto upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("displayName") String displayName,
            @RequestParam("docType") String docType) {
        return service.upload(file, displayName, docType);
    }

    @PutMapping("/{id}")
    public ManagementDocumentDto update(@PathVariable long id, @Valid @RequestBody ManagementDocumentWriteRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(
            @PathVariable long id, @RequestParam(defaultValue = "inline") String disposition) {
        DocumentFile f = service.readFile(id);
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }
}
