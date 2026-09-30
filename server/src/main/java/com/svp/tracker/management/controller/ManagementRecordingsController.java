package com.svp.tracker.management.controller;

import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.dto.ManagementRecordingDetailDto;
import com.svp.tracker.management.dto.ManagementRecordingImageDto;
import com.svp.tracker.management.dto.ManagementRecordingItemDto;
import com.svp.tracker.management.dto.ManagementRecordingListDto;
import com.svp.tracker.management.dto.ManagementRecordingRenameRequestDto;
import com.svp.tracker.management.dto.ManagementRecordingReprocessDto;
import com.svp.tracker.management.dto.ManagementRecordingUploadResultDto;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/management/recordings")
@RequiredArgsConstructor
public class ManagementRecordingsController {

    private final ManagementRecordingsService service;

    @GetMapping
    public ManagementRecordingListDto list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(defaultValue = "LIFE") String desk) {
        return service.list(day, ManagementDesk.fromParam(desk));
    }

    @GetMapping("/search")
    public List<ManagementRecordingItemDto> search(
            @RequestParam String q, @RequestParam(defaultValue = "LIFE") String desk) {
        return service.search(q, ManagementDesk.fromParam(desk));
    }

    @GetMapping("/detail")
    public ManagementRecordingDetailDto detail(
            @RequestParam String path, @RequestParam(defaultValue = "LIFE") String desk) {
        return service.detail(path, ManagementDesk.fromParam(desk));
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> file(
            @RequestParam String path,
            @RequestParam(defaultValue = "inline") String disposition,
            @RequestParam(defaultValue = "LIFE") String desk) {
        RecordingFile f = service.readFile(path, ManagementDesk.fromParam(desk));
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }

    /**
     * Upload a Just Press Record folder or loose files. {@code relativePath} entries keep the folder
     * tree (e.g. {@code 2026-07-25/08-27-11.m4a}). Images in those folders attach to the matching clip.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementRecordingUploadResultDto upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "relativePath", required = false) List<String> relativePaths,
            @RequestParam(defaultValue = "LIFE") String desk) {
        return service.upload(files, relativePaths == null ? List.of() : relativePaths, ManagementDesk.fromParam(desk));
    }

    @PostMapping("/cancel-queue")
    public ManagementRecordingReprocessDto cancelQueue(@RequestParam(defaultValue = "LIFE") String desk) {
        return service.cancelQueue(ManagementDesk.fromParam(desk));
    }

    @PutMapping("/rename")
    public ManagementRecordingDetailDto rename(
            @RequestBody ManagementRecordingRenameRequestDto body,
            @RequestParam(defaultValue = "LIFE") String desk) {
        if (body == null || body.path() == null || body.path().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }
        return service.rename(body.path().trim(), body.displayName(), ManagementDesk.fromParam(desk));
    }

    @GetMapping("/images")
    public List<ManagementRecordingImageDto> listImages(
            @RequestParam String path, @RequestParam(defaultValue = "LIFE") String desk) {
        return service.listImages(path, ManagementDesk.fromParam(desk));
    }

    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<ManagementRecordingImageDto> uploadImages(
            @RequestParam String path,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "LIFE") String desk) {
        return service.uploadImages(path, files, ManagementDesk.fromParam(desk));
    }

    @GetMapping("/images/{id}/file")
    public ResponseEntity<byte[]> imageFile(
            @PathVariable long id, @RequestParam(defaultValue = "inline") String disposition) {
        RecordingFile f = service.readImage(id);
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }

    @DeleteMapping("/images/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable long id) {
        service.deleteImage(id);
    }

    /** Queue or force regenerate transcript + summary for one recording. */
    @PostMapping("/reprocess")
    public ManagementRecordingDetailDto reprocess(
            @RequestBody Map<String, Object> body, @RequestParam(defaultValue = "LIFE") String desk) {
        return service.reprocess(requirePath(body), ManagementDesk.fromParam(desk));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam String path, @RequestParam(defaultValue = "LIFE") String desk) {
        service.delete(path, ManagementDesk.fromParam(desk));
    }

    @PostMapping("/transcribe")
    public ManagementRecordingDetailDto transcribe(
            @RequestBody Map<String, Object> body, @RequestParam(defaultValue = "LIFE") String desk) {
        String path = requirePath(body);
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return service.transcribe(path, force, ManagementDesk.fromParam(desk));
    }

    @PostMapping("/summarize")
    public ManagementRecordingDetailDto summarize(
            @RequestBody Map<String, Object> body, @RequestParam(defaultValue = "LIFE") String desk) {
        String path = requirePath(body);
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return service.summarize(path, force, ManagementDesk.fromParam(desk));
    }

    private static String requirePath(Map<String, Object> body) {
        Object p = body == null ? null : body.get("path");
        if (p == null || p.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }
        return p.toString().trim();
    }
}
