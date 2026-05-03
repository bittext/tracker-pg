package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.domain.ManagementWriteup;
import com.svp.tracker.management.domain.ManagementWriteupAttachment;
import com.svp.tracker.management.dto.ManagementWriteupAttachmentDto;
import com.svp.tracker.management.dto.ManagementWriteupDto;
import com.svp.tracker.management.dto.ManagementWriteupWriteRequest;
import com.svp.tracker.management.repository.ManagementWriteupAttachmentRepository;
import com.svp.tracker.management.repository.ManagementWriteupRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
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
public class ManagementWriteupService {

    private final ManagementWriteupRepository repository;
    private final ManagementWriteupAttachmentRepository attachmentRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementWriteupDto> listForYear(int year) {
        validateYear(year);
        long owner = currentUser.requireUserId();
        return repository.findByOwnerAndYearWithAttachments(owner, year).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ManagementWriteupDto get(long id) {
        ManagementWriteup w = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Write-up not found: " + id));
        assertOwner(w.getOwnerUserId());
        return toDto(w);
    }

    @Transactional
    public ManagementWriteupDto create(ManagementWriteupWriteRequest req) {
        long owner = currentUser.requireUserId();
        validateYear(req.year());
        Instant now = Instant.now();
        ManagementWriteup w = new ManagementWriteup();
        w.setOwnerUserId(owner);
        w.setYear(req.year());
        w.setTopic(req.topic().trim());
        w.setHighlight(normalizeNullable(req.highlight()));
        w.setBody(req.body() == null ? "" : req.body());
        w.setCreatedAt(now);
        w.setUpdatedAt(now);
        w = repository.save(w);
        return toDto(repository.findByIdWithAttachments(w.getId()).orElse(w));
    }

    @Transactional
    public ManagementWriteupDto update(long id, ManagementWriteupWriteRequest req) {
        ManagementWriteup w = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Write-up not found: " + id));
        assertOwner(w.getOwnerUserId());
        validateYear(req.year());
        w.setYear(req.year());
        w.setTopic(req.topic().trim());
        w.setHighlight(normalizeNullable(req.highlight()));
        w.setBody(req.body() == null ? "" : req.body());
        w.setUpdatedAt(Instant.now());
        w = repository.save(w);
        return toDto(repository.findByIdWithAttachments(w.getId()).orElse(w));
    }

    @Transactional
    public void delete(long id) {
        ManagementWriteup w = repository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Write-up not found: " + id));
        assertOwner(w.getOwnerUserId());
        for (ManagementWriteupAttachment a : new ArrayList<>(w.getAttachments())) {
            try {
                blobStore.delete(a.getStorageKey());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        repository.deleteById(id);
    }

    @Transactional
    public ManagementWriteupAttachmentDto addAttachment(long writeupId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        ManagementWriteup w = repository
                .findByIdWithAttachments(writeupId)
                .orElseThrow(() -> new NotFoundException("Write-up not found: " + writeupId));
        assertOwner(w.getOwnerUserId());
        String key;
        try (var in = file.getInputStream()) {
            key = blobStore.put(w.getOwnerUserId(), w.getId(), in, file.getSize());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ManagementWriteupAttachment a = new ManagementWriteupAttachment();
        a.setWriteup(w);
        a.setStorageKey(key);
        a.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setCreatedAt(Instant.now());
        a = attachmentRepository.save(a);
        w.setUpdatedAt(Instant.now());
        return toAttachmentDto(a);
    }

    @Transactional
    public void deleteAttachment(long attachmentId) {
        ManagementWriteupAttachment a = attachmentRepository
                .findByIdWithWriteup(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertOwner(a.getWriteup().getOwnerUserId());
        try {
            blobStore.delete(a.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ManagementWriteup w = a.getWriteup();
        w.getAttachments().remove(a);
        w.setUpdatedAt(Instant.now());
        attachmentRepository.deleteById(attachmentId);
        repository.save(w);
    }

    public record AttachmentFile(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public AttachmentFile readAttachmentFile(long attachmentId) {
        ManagementWriteupAttachment a = attachmentRepository
                .findByIdWithWriteup(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertOwner(a.getWriteup().getOwnerUserId());
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

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void validateYear(int year) {
        if (year < 1970 || year > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
    }

    private void assertOwner(long rowOwnerId) {
        long uid = currentUser.requireUserId();
        if (uid != rowOwnerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private ManagementWriteupDto toDto(ManagementWriteup w) {
        List<ManagementWriteupAttachment> atts = new ArrayList<>(w.getAttachments());
        atts.sort(Comparator.comparing(ManagementWriteupAttachment::getId));
        List<ManagementWriteupAttachmentDto> attDtos = atts.stream().map(this::toAttachmentDto).toList();
        return new ManagementWriteupDto(
                w.getId(),
                w.getOwnerUserId(),
                w.getYear(),
                w.getTopic(),
                w.getHighlight() == null ? "" : w.getHighlight(),
                w.getBody() == null ? "" : w.getBody(),
                attDtos,
                w.getCreatedAt().toString(),
                w.getUpdatedAt().toString());
    }

    private ManagementWriteupAttachmentDto toAttachmentDto(ManagementWriteupAttachment a) {
        return new ManagementWriteupAttachmentDto(
                a.getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                "/api/management/writeups/attachments/" + a.getId() + "/file");
    }
}
