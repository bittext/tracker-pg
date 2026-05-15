package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.domain.ManagementDocument;
import com.svp.tracker.management.dto.ManagementDocumentDto;
import com.svp.tracker.management.dto.ManagementDocumentWriteRequest;
import com.svp.tracker.management.repository.ManagementDocumentRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
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
public class ManagementDocumentsService {

    private final ManagementDocumentRepository repository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementDocumentDto> list() {
        long owner = currentUser.requireUserId();
        return repository.findByOwnerUserIdOrderByCreatedAtDesc(owner).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ManagementDocumentDto upload(MultipartFile file, String displayName, String docType) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        String dn = normalizeName(displayName);
        String dt = normalizeType(docType);
        if (dn.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName required");
        }
        if (dt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "docType required");
        }
        long owner = currentUser.requireUserId();
        String key;
        try (var in = file.getInputStream()) {
            key = blobStore.put(owner, 0L, in, file.getSize());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ManagementDocument d = new ManagementDocument();
        d.setOwnerUserId(owner);
        d.setDisplayName(dn);
        d.setDocType(dt);
        d.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        d.setContentType(file.getContentType());
        d.setByteSize(file.getSize());
        d.setStorageKey(key);
        Instant now = Instant.now();
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        d = repository.save(d);
        return toDto(d);
    }

    @Transactional
    public ManagementDocumentDto update(long id, ManagementDocumentWriteRequest body) {
        ManagementDocument d = repository
                .findByIdAndOwnerUserId(id, currentUser.requireUserId())
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
        d.setDisplayName(normalizeName(body.displayName()));
        d.setDocType(normalizeType(body.docType()));
        d.setUpdatedAt(Instant.now());
        d = repository.save(d);
        return toDto(d);
    }

    @Transactional
    public void delete(long id) {
        ManagementDocument d = repository
                .findByIdAndOwnerUserId(id, currentUser.requireUserId())
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
        try {
            blobStore.delete(d.getStorageKey());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        repository.delete(d);
    }

    public record DocumentFile(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public DocumentFile readFile(long id) {
        ManagementDocument d = repository
                .findByIdAndOwnerUserId(id, currentUser.requireUserId())
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
        try {
            byte[] body = blobStore.readAllBytes(d.getStorageKey());
            String ct = d.getContentType() != null && !d.getContentType().isBlank()
                    ? d.getContentType()
                    : "application/octet-stream";
            String fn = d.getOriginalFilename() != null ? d.getOriginalFilename() : "document";
            return new DocumentFile(ct, fn, body);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private ManagementDocumentDto toDto(ManagementDocument d) {
        String path = "/api/management/documents/" + d.getId() + "/file";
        return new ManagementDocumentDto(
                d.getId(),
                d.getDisplayName(),
                d.getDocType(),
                d.getOriginalFilename(),
                d.getContentType(),
                d.getByteSize(),
                path,
                d.getCreatedAt().toString(),
                d.getUpdatedAt().toString());
    }

    private static String normalizeName(String s) {
        return s == null ? "" : s.trim();
    }

    private static String normalizeType(String s) {
        return s == null ? "" : s.trim();
    }
}
