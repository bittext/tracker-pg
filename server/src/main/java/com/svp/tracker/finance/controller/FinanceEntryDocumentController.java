package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceEntryDocumentDto;
import com.svp.tracker.finance.service.FinanceEntryDocumentService;
import com.svp.tracker.finance.service.FinanceEntryDocumentService.DocumentFile;
import com.svp.tracker.finance.service.FinanceEntryEntityType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/finance/entry-documents")
@RequiredArgsConstructor
@Slf4j
public class FinanceEntryDocumentController {

    private final FinanceEntryDocumentService documentService;

    @GetMapping
    public List<FinanceEntryDocumentDto> list(
            @RequestParam String entityType, @RequestParam long entityId) {
        return documentService.listForEntity(FinanceEntryEntityType.parse(entityType), entityId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceEntryDocumentDto upload(
            @RequestParam String entityType,
            @RequestParam long entityId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String displayName) {
        log.info("POST /api/finance/entry-documents entityType={} entityId={}", entityType, entityId);
        return documentService.upload(FinanceEntryEntityType.parse(entityType), entityId, file, displayName);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/entry-documents/{}", id);
        documentService.delete(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(
            @PathVariable long id, @RequestParam(defaultValue = "attachment") String disposition) {
        DocumentFile f = documentService.readFile(id);
        String mode = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }
}
