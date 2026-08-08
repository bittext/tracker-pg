package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.finance.domain.FinanceEntryDocument;
import com.svp.tracker.finance.dto.FinanceEntryDocumentDto;
import com.svp.tracker.finance.repository.FinanceCreditCardRepository;
import com.svp.tracker.finance.repository.FinanceCreditStandingRepository;
import com.svp.tracker.finance.repository.FinanceEntryDocumentRepository;
import com.svp.tracker.finance.repository.FinanceInsurancePolicyRepository;
import com.svp.tracker.finance.repository.FinanceInvestmentRepository;
import com.svp.tracker.finance.repository.FinanceLoanRepository;
import com.svp.tracker.journal.service.JournalBlobStore;
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
public class FinanceEntryDocumentService {

    private final CurrentUserService currentUser;
    private final FinanceEntryDocumentRepository documentRepository;
    private final FinanceInvestmentRepository investmentRepository;
    private final FinanceLoanRepository loanRepository;
    private final FinanceCreditCardRepository creditCardRepository;
    private final FinanceInsurancePolicyRepository insurancePolicyRepository;
    private final FinanceCreditStandingRepository creditStandingRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;

    @Transactional(readOnly = true)
    public List<FinanceEntryDocumentDto> listForEntity(FinanceEntryEntityType entityType, long entityId) {
        long uid = currentUser.requireUserId();
        assertEntityOwned(entityType, entityId, uid);
        return documentRepository
                .findByOwnerUserIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(uid, entityType.wire(), entityId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countForEntity(FinanceEntryEntityType entityType, long entityId, long ownerUserId) {
        return documentRepository.countByOwnerUserIdAndEntityTypeAndEntityId(
                ownerUserId, entityType.wire(), entityId);
    }

    @Transactional
    public FinanceEntryDocumentDto upload(FinanceEntryEntityType entityType, long entityId, MultipartFile file, String displayName) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        long uid = currentUser.requireUserId();
        assertEntityOwned(entityType, entityId, uid);
        String key;
        try (var in = file.getInputStream()) {
            key = blobStore.put(uid, entityId, in, file.getSize());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        FinanceEntryDocument row = new FinanceEntryDocument();
        row.setOwnerUserId(uid);
        row.setEntityType(entityType.wire());
        row.setEntityId(entityId);
        row.setStorageKey(key);
        row.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        row.setContentType(file.getContentType());
        row.setSizeBytes(file.getSize());
        row.setDisplayName(normalizeDisplayName(displayName, row.getOriginalFilename()));
        row.setCreatedAt(Instant.now());
        return toDto(documentRepository.save(row));
    }

    @Transactional
    public void delete(long documentId) {
        long uid = currentUser.requireUserId();
        FinanceEntryDocument row = documentRepository
                .findByIdAndOwnerUserId(documentId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        deleteRow(row);
    }

    @Transactional
    public void deleteAllForEntity(FinanceEntryEntityType entityType, long entityId, long ownerUserId) {
        List<FinanceEntryDocument> rows =
                documentRepository.findByOwnerUserIdAndEntityTypeAndEntityId(ownerUserId, entityType.wire(), entityId);
        for (FinanceEntryDocument row : rows) {
            deleteRow(row);
        }
    }

    @Transactional(readOnly = true)
    public DocumentFile readFile(long documentId) {
        long uid = currentUser.requireUserId();
        FinanceEntryDocument row = documentRepository
                .findByIdAndOwnerUserId(documentId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        try {
            byte[] body = blobStore.readAllBytes(row.getStorageKey());
            String contentType = row.getContentType() != null && !row.getContentType().isBlank()
                    ? row.getContentType()
                    : "application/octet-stream";
            String filename = row.getDisplayName() != null && !row.getDisplayName().isBlank()
                    ? row.getDisplayName()
                    : row.getOriginalFilename();
            return new DocumentFile(contentType, filename, body);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void assertEntityOwned(FinanceEntryEntityType entityType, long entityId, long ownerUserId) {
        boolean owned =
                switch (entityType) {
                    case INVESTMENT -> investmentRepository.findByIdAndOwnerUserId(entityId, ownerUserId).isPresent();
                    case LOAN -> loanRepository.findByIdAndOwnerUserId(entityId, ownerUserId).isPresent();
                    case CREDIT_CARD -> creditCardRepository.findByIdAndOwnerUserId(entityId, ownerUserId).isPresent();
                    case INSURANCE -> insurancePolicyRepository.findByIdAndOwnerUserId(entityId, ownerUserId).isPresent();
                    case CREDIT_STANDING -> creditStandingRepository
                            .findByOwnerUserId(ownerUserId)
                            .map(s -> Objects.equals(s.getId(), entityId))
                            .orElse(false);
                };
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, entityType.wire() + " entry not found");
        }
    }

    private void deleteRow(FinanceEntryDocument row) {
        try {
            blobStore.delete(row.getStorageKey());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        documentRepository.delete(row);
    }

    private FinanceEntryDocumentDto toDto(FinanceEntryDocument row) {
        return new FinanceEntryDocumentDto(
                row.getId(),
                row.getEntityType(),
                row.getEntityId(),
                row.getOriginalFilename(),
                row.getDisplayName() == null ? "" : row.getDisplayName(),
                row.getContentType() == null ? "" : row.getContentType(),
                row.getSizeBytes(),
                row.getCreatedAt());
    }

    private static String normalizeDisplayName(String displayName, String fallback) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return fallback;
    }

    public record DocumentFile(String contentType, String filename, byte[] body) {}
}
