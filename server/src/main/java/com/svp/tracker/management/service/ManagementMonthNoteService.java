package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.domain.ManagementMonthNote;
import com.svp.tracker.management.domain.ManagementMonthNoteAttachment;
import com.svp.tracker.management.dto.ManagementMonthNoteAttachmentDto;
import com.svp.tracker.management.dto.ManagementMonthNoteCalendarDto;
import com.svp.tracker.management.dto.ManagementMonthNoteDto;
import com.svp.tracker.management.dto.ManagementMonthNoteWriteRequest;
import com.svp.tracker.management.repository.ManagementMonthNoteAttachmentRepository;
import com.svp.tracker.management.repository.ManagementMonthNoteRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementMonthNoteService {

    private final ManagementMonthNoteRepository noteRepository;
    private final ManagementMonthNoteAttachmentRepository attachmentRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public ManagementMonthNoteCalendarDto calendar(int year) {
        if (year < 1970 || year > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
        long owner = currentUser.requireUserId();
        List<Object[]> rows = noteRepository.countByMonthForYear(owner, year);
        Map<Integer, Long> byMonth = new HashMap<>();
        for (Object[] r : rows) {
            byMonth.put((Integer) r[0], (Long) r[1]);
        }
        List<ManagementMonthNoteCalendarDto.MonthCount> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            months.add(new ManagementMonthNoteCalendarDto.MonthCount(m, byMonth.getOrDefault(m, 0L)));
        }
        return new ManagementMonthNoteCalendarDto(year, months);
    }

    @Transactional(readOnly = true)
    public List<ManagementMonthNoteDto> list(int year, Integer month) {
        if (year < 1970 || year > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
        long owner = currentUser.requireUserId();
        List<ManagementMonthNote> list;
        if (month == null) {
            list = noteRepository.findByOwnerAndYearWithAttachments(owner, year);
        } else {
            if (month < 1 || month > 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month");
            }
            list = noteRepository.findByOwnerAndYearMonthWithAttachments(owner, year, month);
        }
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ManagementMonthNoteDto get(long id) {
        ManagementMonthNote n = noteRepository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Note not found: " + id));
        assertRowAccess(n.getOwnerUserId());
        return toDto(n);
    }

    @Transactional
    public ManagementMonthNoteDto create(ManagementMonthNoteWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        ManagementMonthNote n = new ManagementMonthNote();
        n.setOwnerUserId(owner);
        n.setYear(req.year());
        n.setMonth(req.month());
        n.setSubject(req.subject().trim());
        n.setBody(req.body() == null ? "" : req.body());
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        n = noteRepository.save(n);
        return toDto(noteRepository.findByIdWithAttachments(n.getId()).orElse(n));
    }

    @Transactional
    public ManagementMonthNoteDto update(long id, ManagementMonthNoteWriteRequest req) {
        ManagementMonthNote n = noteRepository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Note not found: " + id));
        assertRowAccess(n.getOwnerUserId());
        n.setYear(req.year());
        n.setMonth(req.month());
        n.setSubject(req.subject().trim());
        n.setBody(req.body() == null ? "" : req.body());
        n.setUpdatedAt(Instant.now());
        n = noteRepository.save(n);
        return toDto(noteRepository.findByIdWithAttachments(n.getId()).orElse(n));
    }

    @Transactional
    public void delete(long id) {
        ManagementMonthNote n = noteRepository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Note not found: " + id));
        assertRowAccess(n.getOwnerUserId());
        for (ManagementMonthNoteAttachment a : new ArrayList<>(n.getAttachments())) {
            try {
                blobStore.delete(a.getStorageKey());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        noteRepository.deleteById(id);
    }

    @Transactional
    public ManagementMonthNoteAttachmentDto addAttachment(long noteId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        ManagementMonthNote n = noteRepository
                .findByIdWithAttachments(noteId)
                .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
        assertRowAccess(n.getOwnerUserId());
        String key;
        try (var in = file.getInputStream()) {
            key = blobStore.put(n.getOwnerUserId(), n.getId(), in, file.getSize());
        }
        ManagementMonthNoteAttachment a = new ManagementMonthNoteAttachment();
        a.setNote(n);
        a.setStorageKey(key);
        a.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setCreatedAt(Instant.now());
        a = attachmentRepository.save(a);
        n.getAttachments().add(a);
        n.setUpdatedAt(Instant.now());
        noteRepository.save(n);
        return toAttachmentDto(a);
    }

    @Transactional
    public void deleteAttachment(long attachmentId) {
        ManagementMonthNoteAttachment a = attachmentRepository
                .findByIdWithNote(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getNote().getOwnerUserId());
        try {
            blobStore.delete(a.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ManagementMonthNote n = a.getNote();
        n.getAttachments().remove(a);
        n.setUpdatedAt(Instant.now());
        attachmentRepository.deleteById(attachmentId);
        noteRepository.save(n);
    }

    public record AttachmentFile(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public AttachmentFile readAttachmentFile(long attachmentId) {
        ManagementMonthNoteAttachment a = attachmentRepository
                .findByIdWithNote(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getNote().getOwnerUserId());
        try {
            byte[] body = blobStore.readAllBytes(a.getStorageKey());
            return new AttachmentFile(
                    a.getContentType() != null ? a.getContentType() : "application/octet-stream",
                    a.getOriginalFilename(),
                    body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertRowAccess(Long ownerUserId) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private ManagementMonthNoteDto toDto(ManagementMonthNote n) {
        List<ManagementMonthNoteAttachment> atts = new ArrayList<>(n.getAttachments());
        atts.sort(Comparator.comparing(ManagementMonthNoteAttachment::getId));
        List<ManagementMonthNoteAttachmentDto> attDtos = atts.stream().map(this::toAttachmentDto).toList();
        return new ManagementMonthNoteDto(
                n.getId(),
                n.getOwnerUserId(),
                n.getYear(),
                n.getMonth(),
                n.getSubject(),
                n.getBody() == null ? "" : n.getBody(),
                attDtos,
                n.getCreatedAt(),
                n.getUpdatedAt());
    }

    private ManagementMonthNoteAttachmentDto toAttachmentDto(ManagementMonthNoteAttachment a) {
        return new ManagementMonthNoteAttachmentDto(
                a.getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                "/api/management/notes/attachments/" + a.getId() + "/file");
    }
}
