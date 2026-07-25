package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.ManagementRecordingDetailDto;
import com.svp.tracker.management.dto.ManagementRecordingItemDto;
import com.svp.tracker.management.dto.ManagementRecordingListDto;
import com.svp.tracker.management.service.ManagementRecordingsService;
import com.svp.tracker.management.service.ManagementRecordingsService.RecordingFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/recordings")
@RequiredArgsConstructor
public class ManagementRecordingsController {

    private final ManagementRecordingsService service;

    @GetMapping
    public ManagementRecordingListDto list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return service.list(day);
    }

    @GetMapping("/search")
    public List<ManagementRecordingItemDto> search(@RequestParam String q) {
        return service.search(q);
    }

    @GetMapping("/detail")
    public ManagementRecordingDetailDto detail(@RequestParam String path) {
        return service.detail(path);
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> file(
            @RequestParam String path, @RequestParam(defaultValue = "inline") String disposition) {
        RecordingFile f = service.readFile(path);
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }

    @PostMapping("/transcribe")
    public ManagementRecordingDetailDto transcribe(@RequestBody Map<String, Object> body) {
        String path = requirePath(body);
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return service.transcribe(path, force);
    }

    @PostMapping("/summarize")
    public ManagementRecordingDetailDto summarize(@RequestBody Map<String, Object> body) {
        String path = requirePath(body);
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return service.summarize(path, force);
    }

    private static String requirePath(Map<String, Object> body) {
        Object p = body == null ? null : body.get("path");
        if (p == null || p.toString().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "path is required");
        }
        return p.toString().trim();
    }
}
