package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.domain.ManagementWorkLogAttachment;
import com.svp.tracker.management.domain.ManagementWorkLogEntry;
import com.svp.tracker.management.dto.ManagementWorkLogAttachmentDto;
import com.svp.tracker.management.dto.ManagementWorkLogCalendarDto;
import com.svp.tracker.management.dto.ManagementWorkLogEntryDto;
import com.svp.tracker.management.dto.ManagementWorkLogEntryWriteRequest;
import com.svp.tracker.management.repository.ManagementWorkLogAttachmentRepository;
import com.svp.tracker.management.repository.ManagementWorkLogEntryRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementWorkLogService {

    private final ManagementWorkLogEntryRepository repository;
    private final ManagementWorkLogAttachmentRepository attachmentRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementWorkLogEntryDto> listBetween(LocalDate from, LocalDate to) {
        validateRange(from, to);
        long owner = currentUser.requireUserId();
        return repository.findByOwnerAndEntryDateBetweenWithAttachments(owner, from, to).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ManagementWorkLogEntryDto> listForDay(LocalDate date) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        long owner = currentUser.requireUserId();
        return repository.findByOwnerAndEntryDateWithAttachments(owner, date).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagementWorkLogCalendarDto calendar(int year) {
        if (year < 1970 || year > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
        long owner = currentUser.requireUserId();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<Object[]> rows = repository.countByEntryDateInRange(owner, from, to);
        List<ManagementWorkLogCalendarDto.DayCount> days = new ArrayList<>();
        for (Object[] r : rows) {
            LocalDate d = (LocalDate) r[0];
            long c = (Long) r[1];
            days.add(new ManagementWorkLogCalendarDto.DayCount(d.toString(), c));
        }
        days.sort(Comparator.comparing(ManagementWorkLogCalendarDto.DayCount::date));
        return new ManagementWorkLogCalendarDto(year, days);
    }

    @Transactional(readOnly = true)
    public ManagementWorkLogEntryDto get(long id) {
        ManagementWorkLogEntry e = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Work log entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        return toDto(e);
    }

    @Transactional
    public ManagementWorkLogEntryDto create(ManagementWorkLogEntryWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        ManagementWorkLogEntry e = new ManagementWorkLogEntry();
        e.setOwnerUserId(owner);
        e.setEntryDate(req.entryDate());
        e.setLoggedAt(now);
        e.setSubject(normalizeSubject(req.subject()));
        e.setBody(req.body() == null ? "" : req.body());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e = repository.save(e);
        return toDto(repository.findByIdWithAttachments(e.getId()).orElse(e));
    }

    @Transactional
    public ManagementWorkLogEntryDto update(long id, ManagementWorkLogEntryWriteRequest req) {
        ManagementWorkLogEntry e = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Work log entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        e.setEntryDate(req.entryDate());
        e.setSubject(normalizeSubject(req.subject()));
        e.setBody(req.body() == null ? "" : req.body());
        e.setUpdatedAt(Instant.now());
        e = repository.save(e);
        return toDto(repository.findByIdWithAttachments(e.getId()).orElse(e));
    }

    @Transactional
    public void delete(long id) {
        ManagementWorkLogEntry e = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Work log entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        for (ManagementWorkLogAttachment a : new ArrayList<>(e.getAttachments())) {
            try {
                blobStore.delete(a.getStorageKey());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        repository.delete(e);
    }

    @Transactional
    public ManagementWorkLogAttachmentDto addAttachment(long entryId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        ManagementWorkLogEntry e = repository
                .findByIdWithAttachments(entryId)
                .orElseThrow(() -> new NotFoundException("Work log entry not found: " + entryId));
        assertRowAccess(e.getOwnerUserId());
        String key;
        try (var in = file.getInputStream()) {
            key = blobStore.put(e.getOwnerUserId(), e.getId(), in, file.getSize());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ManagementWorkLogAttachment a = new ManagementWorkLogAttachment();
        a.setEntry(e);
        a.setStorageKey(key);
        a.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setCreatedAt(Instant.now());
        a = attachmentRepository.save(a);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        return toAttachmentDto(a);
    }

    @Transactional
    public void deleteAttachment(long attachmentId) {
        ManagementWorkLogAttachment a = attachmentRepository
                .findByIdWithEntry(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getEntry().getOwnerUserId());
        try {
            blobStore.delete(a.getStorageKey());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ManagementWorkLogEntry e = a.getEntry();
        e.getAttachments().remove(a);
        e.setUpdatedAt(Instant.now());
        attachmentRepository.deleteById(attachmentId);
        repository.save(e);
    }

    public record AttachmentFile(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public AttachmentFile readAttachmentFile(long attachmentId) {
        ManagementWorkLogAttachment a = attachmentRepository
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

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required (yyyy-MM-dd)");
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
        }
    }

    private static String normalizeSubject(String subject) {
        if (subject == null) {
            return "";
        }
        String t = subject.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private void assertRowAccess(Long ownerUserId) {
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private ManagementWorkLogEntryDto toDto(ManagementWorkLogEntry e) {
        List<ManagementWorkLogAttachment> atts = new ArrayList<>(e.getAttachments());
        atts.sort(Comparator.comparing(ManagementWorkLogAttachment::getId));
        List<ManagementWorkLogAttachmentDto> attDtos = atts.stream().map(this::toAttachmentDto).toList();
        return new ManagementWorkLogEntryDto(
                e.getId(),
                e.getOwnerUserId(),
                e.getEntryDate(),
                e.getLoggedAt(),
                e.getSubject() == null ? "" : e.getSubject(),
                e.getBody() == null ? "" : e.getBody(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                attDtos);
    }

    private ManagementWorkLogAttachmentDto toAttachmentDto(ManagementWorkLogAttachment a) {
        return new ManagementWorkLogAttachmentDto(
                a.getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                "/api/management/work-log/attachments/" + a.getId() + "/file");
    }
}
