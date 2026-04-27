package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.finance.domain.FinanceTax1040Return;
import com.svp.tracker.finance.dto.FinanceTax1040ReturnDto;
import com.svp.tracker.finance.repository.FinanceTax1040ReturnRepository;
import com.svp.tracker.finance.tax.Form1040ParsedSummary;
import com.svp.tracker.finance.tax.Form1040TextParser;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinanceTax1040Service {

    private static final int EXTRACT_MAX_CHARS = 80_000;
    private static final int LIST_PREVIEW_CHARS = 2_500;

    private final FinanceTax1040ReturnRepository repository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<FinanceTax1040ReturnDto> list(boolean includeFullExtract) {
        long owner = currentUser.requireUserId();
        return repository.findByOwnerUserIdOrderByTaxYearDesc(owner).stream()
                .map(r -> toDto(r, includeFullExtract))
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanceTax1040ReturnDto get(long id, boolean includeFullExtract) {
        FinanceTax1040Return r = repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("1040 return not found: " + id));
        assertAccess(r.getOwnerUserId());
        return toDto(r, includeFullExtract);
    }

    @Transactional
    public FinanceTax1040ReturnDto uploadOrReplace(int taxYear, MultipartFile file) throws IOException {
        if (taxYear < 1990 || taxYear > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxYear must be between 1990 and 2100");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF file required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        if (!isPdf(file)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Upload a PDF copy of your Form 1040 (application/pdf or .pdf).");
        }

        long owner = currentUser.requireUserId();
        byte[] bytes = file.getBytes();
        String extracted = extractPdfText(bytes);
        if (extracted != null && extracted.length() > EXTRACT_MAX_CHARS) {
            extracted = extracted.substring(0, EXTRACT_MAX_CHARS) + "\n…";
        }
        Form1040ParsedSummary summary = Form1040TextParser.parse(extracted);
        String summaryJson = writeJson(summary);

        String newKey;
        try (var in = new ByteArrayInputStream(bytes)) {
            newKey = blobStore.put(owner, taxYear, in, bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        FinanceTax1040Return existing = repository.findByOwnerUserIdAndTaxYear(owner, taxYear).orElse(null);
        String oldKey = existing != null ? existing.getStorageKey() : null;

        try {
            Instant now = Instant.now();
            FinanceTax1040Return row = existing != null ? existing : new FinanceTax1040Return();
            row.setOwnerUserId(owner);
            row.setTaxYear(taxYear);
            row.setStorageKey(newKey);
            row.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "1040.pdf"));
            row.setContentType(file.getContentType());
            row.setSizeBytes(file.getSize());
            row.setExtractedText(extracted);
            row.setSummaryJson(summaryJson);
            if (existing == null) {
                row.setCreatedAt(now);
            }
            row.setUpdatedAt(now);
            row = repository.save(row);

            if (oldKey != null && !oldKey.equals(newKey)) {
                try {
                    blobStore.delete(oldKey);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return toDto(row, true);
        } catch (RuntimeException e) {
            try {
                blobStore.delete(newKey);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            throw e;
        }
    }

    @Transactional
    public void delete(long id) {
        FinanceTax1040Return r = repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("1040 return not found: " + id));
        assertAccess(r.getOwnerUserId());
        try {
            blobStore.delete(r.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        repository.deleteById(id);
    }

    public record Tax1040FileContent(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public Tax1040FileContent readFile(long id) {
        FinanceTax1040Return r = repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("1040 return not found: " + id));
        assertAccess(r.getOwnerUserId());
        try {
            byte[] body = blobStore.readAllBytes(r.getStorageKey());
            return new Tax1040FileContent(
                    r.getContentType() != null ? r.getContentType() : "application/pdf",
                    r.getOriginalFilename(),
                    body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertAccess(Long ownerUserId) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private FinanceTax1040ReturnDto toDto(FinanceTax1040Return r, boolean includeFullExtract) {
        String full = r.getExtractedText();
        String preview = full;
        if (preview != null && preview.length() > LIST_PREVIEW_CHARS) {
            preview = preview.substring(0, LIST_PREVIEW_CHARS) + "…";
        }
        return new FinanceTax1040ReturnDto(
                r.getId(),
                r.getTaxYear(),
                r.getOriginalFilename(),
                r.getSizeBytes(),
                "/api/finance/tax/1040/returns/" + r.getId() + "/download",
                readSummary(r.getSummaryJson()),
                preview,
                includeFullExtract ? full : null,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private Form1040ParsedSummary readSummary(String json) {
        try {
            return objectMapper.readValue(json, Form1040ParsedSummary.class);
        } catch (JsonProcessingException e) {
            return Form1040ParsedSummary.builder()
                    .likelyForm1040(false)
                    .parseNote("Could not read stored summary.")
                    .build();
        }
    }

    private String writeJson(Form1040ParsedSummary s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isPdf(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ct.toLowerCase(Locale.ROOT).contains("pdf")) {
            return true;
        }
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static String extractPdfText(byte[] data) {
        if (data == null || data.length < 5) {
            return null;
        }
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(data))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String t = stripper.getText(doc);
            return t == null ? null : t.trim();
        } catch (IOException e) {
            return "(Could not read PDF text: " + e.getMessage() + ")";
        }
    }
}
