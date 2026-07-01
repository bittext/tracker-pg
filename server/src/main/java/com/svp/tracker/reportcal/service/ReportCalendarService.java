package com.svp.tracker.reportcal.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.service.ManagementCalendarTypeService;
import com.svp.tracker.reportcal.domain.ReportCalendarAttachment;
import com.svp.tracker.reportcal.domain.ReportCalendarEntry;
import com.svp.tracker.reportcal.dto.ReportCalendarAttachmentDto;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryDto;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryWriteDto;
import com.svp.tracker.reportcal.repository.ReportCalendarAttachmentRepository;
import com.svp.tracker.reportcal.repository.ReportCalendarEntryRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportCalendarService {

    private final ReportCalendarEntryRepository repository;
    private final ReportCalendarAttachmentRepository attachmentRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;
    private final ManagementCalendarTypeService calendarTypeService;

    @Transactional(readOnly = true)
    public List<ReportCalendarEntryDto> listInRange(LocalDate from, LocalDate to, @Nullable String type) {
        long uid = currentUser.requireUserId();
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to before from");
        }
        List<ReportCalendarEntry> rows =
                type == null
                        ? repository.findByOwnerUserIdAndEntryDateBetweenWithAttachments(uid, from, to)
                        : repository.findByOwnerUserIdAndCalendarTypeAndEntryDateBetweenWithAttachments(
                                uid, type, from, to);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public ReportCalendarEntryDto create(ReportCalendarEntryWriteDto body) {
        long uid = currentUser.requireUserId();
        String title = trimToNull(body.getTitle());
        String text = trimToNull(body.getBody());
        String details = trimToNull(body.getDetails());
        if (title == null && text == null && details == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title, information, or details is required");
        }
        ReportCalendarEntry e = new ReportCalendarEntry();
        e.setOwnerUserId(uid);
        e.setEntryDate(body.getEntryDate());
        e.setCalendarType(calendarTypeService.assertValidForUser(uid, body.getCalendarType()));
        e.setTitle(title);
        e.setBody(text);
        e.setDetails(details);
        e = repository.save(e);
        return toDto(repository.findByIdWithAttachments(e.getId()).orElse(e));
    }

    @Transactional
    public ReportCalendarEntryDto update(long id, ReportCalendarEntryWriteDto body) {
        ReportCalendarEntry e = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
        assertRowAccess(e.getOwnerUserId());
        String title = trimToNull(body.getTitle());
        String text = trimToNull(body.getBody());
        String details = trimToNull(body.getDetails());
        if (title == null && text == null && details == null && e.getAttachments().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title, information, or details is required");
        }
        e.setEntryDate(body.getEntryDate());
        e.setCalendarType(calendarTypeService.assertValidForUser(e.getOwnerUserId(), body.getCalendarType()));
        e.setTitle(title);
        e.setBody(text);
        e.setDetails(details);
        e = repository.save(e);
        return toDto(repository.findByIdWithAttachments(e.getId()).orElse(e));
    }

    @Transactional
    public void delete(long id) {
        ReportCalendarEntry e = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
        assertRowAccess(e.getOwnerUserId());
        for (ReportCalendarAttachment a : new ArrayList<>(e.getAttachments())) {
            try {
                blobStore.delete(a.getStorageKey());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            e.getAttachments().remove(a);
            attachmentRepository.delete(a);
        }
        repository.delete(e);
    }

    @Transactional
    public ReportCalendarAttachmentDto addAttachment(long entryId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        ReportCalendarEntry e = repository
                .findByIdWithAttachments(entryId)
                .orElseThrow(() -> new NotFoundException("Entry not found: " + entryId));
        assertRowAccess(e.getOwnerUserId());
        String key;
        try (var in = file.getInputStream()) {
            key = blobStore.put(e.getOwnerUserId(), e.getId(), in, file.getSize());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ReportCalendarAttachment a = new ReportCalendarAttachment();
        a.setEntry(e);
        a.setStorageKey(key);
        a.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setCreatedAt(Instant.now());
        a = attachmentRepository.save(a);
        e.setUpdatedAt(Instant.now());
        return toAttachmentDto(a);
    }

    @Transactional
    public void deleteAttachment(long attachmentId) {
        ReportCalendarAttachment a = attachmentRepository
                .findByIdWithEntry(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getEntry().getOwnerUserId());
        try {
            blobStore.delete(a.getStorageKey());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ReportCalendarEntry e = a.getEntry();
        e.getAttachments().remove(a);
        e.setUpdatedAt(Instant.now());
        attachmentRepository.deleteById(attachmentId);
        repository.save(e);
    }

    public record AttachmentFile(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public AttachmentFile readAttachmentFile(long attachmentId) {
        ReportCalendarAttachment a = attachmentRepository
                .findByIdWithEntry(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getEntry().getOwnerUserId());
        try {
            byte[] body = blobStore.readAllBytes(a.getStorageKey());
            return new AttachmentFile(
                    a.getContentType() != null ? a.getContentType() : "application/octet-stream",
                    a.getOriginalFilename(),
                    body);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void assertRowAccess(Long ownerUserId) {
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ReportCalendarEntryDto toDto(ReportCalendarEntry e) {
        List<ReportCalendarAttachment> atts = new ArrayList<>(e.getAttachments());
        atts.sort(Comparator.comparing(ReportCalendarAttachment::getId));
        List<ReportCalendarAttachmentDto> attDtos = atts.stream().map(this::toAttachmentDto).toList();
        return ReportCalendarEntryDto.builder()
                .id(e.getId())
                .entryDate(e.getEntryDate())
                .calendarType(e.getCalendarType())
                .title(e.getTitle())
                .body(e.getBody())
                .details(e.getDetails())
                .attachments(attDtos)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private ReportCalendarAttachmentDto toAttachmentDto(ReportCalendarAttachment a) {
        return new ReportCalendarAttachmentDto(
                a.getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                "/api/report-calendar/attachments/" + a.getId() + "/file");
    }
}
