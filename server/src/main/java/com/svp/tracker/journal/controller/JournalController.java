package com.svp.tracker.journal.controller;

import com.svp.tracker.journal.dto.JournalAttachmentDto;
import com.svp.tracker.journal.dto.JournalCalendarDayDto;
import com.svp.tracker.journal.dto.JournalEntryDto;
import com.svp.tracker.journal.dto.JournalEntryWriteRequest;
import com.svp.tracker.journal.dto.JournalSummaryDto;
import com.svp.tracker.journal.dto.JournalTagDefDto;
import com.svp.tracker.journal.service.JournalService;
import com.svp.tracker.journal.service.JournalService.AttachmentFile;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;

    @GetMapping("/tag-definitions")
    public List<JournalTagDefDto> listTagDefinitions() {
        return journalService.listTagDefinitions();
    }

    @PostMapping("/tag-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalTagDefDto createTag(@RequestBody java.util.Map<String, String> body) {
        String name = body != null ? body.get("name") : null;
        return journalService.createTag(name);
    }

    @DeleteMapping("/tag-definitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable long id) {
        journalService.deleteTag(id);
    }

    @GetMapping("/calendar")
    public List<JournalCalendarDayDto> calendar(
            @RequestParam int year, @RequestParam int month) {
        return journalService.calendar(year, month);
    }

    @GetMapping("/entries/day")
    public List<JournalEntryDto> listForDay(@RequestParam LocalDate date) {
        return journalService.listEntriesForDay(date);
    }

    @GetMapping("/entries/search")
    public List<JournalEntryDto> search(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(name = "tagIds", required = false) @Nullable Long[] tagIds) {
        return journalService.search(from, to, q, toTagIdList(tagIds));
    }

    @GetMapping("/summary")
    public JournalSummaryDto summary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(name = "tagIds", required = false) @Nullable Long[] tagIds) {
        return journalService.summarize(from, to, q, toTagIdList(tagIds));
    }

    /** Binds each {@code &tagIds=} query parameter; more reliable for multi-value GET than {@link List} on some servers. */
    private static List<Long> toTagIdList(Long[] tagIds) {
        if (tagIds == null || tagIds.length == 0) {
            return null;
        }
        return Arrays.stream(tagIds)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @GetMapping("/entries/{id}")
    public JournalEntryDto getEntry(@PathVariable long id) {
        return journalService.getEntry(id);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntryDto create(@Valid @RequestBody JournalEntryWriteRequest body) {
        return journalService.create(body);
    }

    @PutMapping("/entries/{id}")
    public JournalEntryDto update(@PathVariable long id, @Valid @RequestBody JournalEntryWriteRequest body) {
        return journalService.update(id, body);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable long id) {
        journalService.deleteEntry(id);
    }

    @PostMapping("/entries/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalAttachmentDto uploadAttachment(
            @PathVariable long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientLastModifiedMs", required = false) Long clientLastModifiedMs)
            throws IOException {
        return journalService.addAttachment(id, file, clientLastModifiedMs);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable long attachmentId) {
        journalService.deleteAttachment(attachmentId);
    }

    @GetMapping("/attachments/{id}/file")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable("id") long attachmentId,
            @RequestParam(defaultValue = "inline") String disposition) {
        AttachmentFile f = journalService.readAttachment(attachmentId);
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode)
                        .filename(f.filename(), StandardCharsets.UTF_8)
                        .build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }
}
