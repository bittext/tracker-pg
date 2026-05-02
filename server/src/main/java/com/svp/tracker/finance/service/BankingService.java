package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.BankingImportProperties;
import com.svp.tracker.finance.domain.BankingFileKind;
import com.svp.tracker.finance.domain.BankingImportFile;
import com.svp.tracker.finance.domain.BankingInstitution;
import com.svp.tracker.finance.domain.BankingTransaction;
import com.svp.tracker.finance.dto.BankingCreateInstitutionRequestDto;
import com.svp.tracker.finance.dto.BankingImportFileDto;
import com.svp.tracker.finance.dto.BankingImportResultDto;
import com.svp.tracker.finance.dto.BankingInstitutionDto;
import com.svp.tracker.finance.dto.BankingLedgerDto;
import com.svp.tracker.finance.dto.BankingLedgerRange;
import com.svp.tracker.finance.dto.BankingTransactionDto;
import com.svp.tracker.finance.repository.BankingImportFileRepository;
import com.svp.tracker.finance.repository.BankingInstitutionRepository;
import com.svp.tracker.finance.repository.BankingTransactionRepository;
import com.svp.tracker.finance.service.banking.BankingFormatParser;
import com.svp.tracker.finance.service.banking.BankingHashUtil;
import com.svp.tracker.finance.service.banking.BankingParseOutcome;
import com.svp.tracker.finance.service.banking.BankingParsedRow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankingService {

    private static final int HASH_LOOKUP_CHUNK = 800;

    private final BankingImportProperties bankingProps;
    private final CurrentUserService currentUserService;
    private final BankingInstitutionRepository institutionRepository;
    private final BankingImportFileRepository importFileRepository;
    private final BankingTransactionRepository transactionRepository;

    public List<BankingInstitutionDto> listInstitutions() {
        long uid = currentUserService.requireUserId();
        return institutionRepository.findByOwnerUserIdOrderByNameAsc(uid).stream()
                .map(BankingService::toInstitutionDto)
                .toList();
    }

    @Transactional
    public BankingInstitutionDto createInstitution(BankingCreateInstitutionRequestDto req) {
        long uid = currentUserService.requireUserId();
        String name = req.name().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution name is required");
        }
        if (institutionRepository.existsByOwnerUserIdAndNameIgnoreCase(uid, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An institution with that name already exists");
        }
        BankingInstitution e = new BankingInstitution();
        e.setOwnerUserId(uid);
        e.setName(name);
        e = institutionRepository.save(e);
        return toInstitutionDto(e);
    }

    @Transactional
    public BankingImportResultDto importFile(long institutionId, MultipartFile multipart) throws IOException {
        long uid = currentUserService.requireUserId();
        BankingInstitution inst = institutionRepository
                .findByIdAndOwnerUserId(institutionId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        String importRoot = bankingProps.importDirectory();
        if (importRoot.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Banking import directory is not configured (tracker.finance.banking.import-directory)");
        }
        if (multipart == null || multipart.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file");
        }
        byte[] raw = multipart.getBytes();
        if (raw.length > bankingProps.maxUploadBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File exceeds tracker.finance.banking.max-upload-bytes=" + bankingProps.maxUploadBytes());
        }
        String originalName = Optional.ofNullable(multipart.getOriginalFilename()).orElse("upload");
        String sha = BankingHashUtil.sha256Hex(raw);
        if (importFileRepository.existsByOwnerUserIdAndSha256Hex(uid, sha)) {
            return new BankingImportResultDto(
                    true,
                    true,
                    null,
                    "This file was already imported (same SHA-256); skipped.");
        }

        Path root = Path.of(importRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Cannot create import directory: " + root, e);
            }
        }

        String ext = BankingFormatParser.extension(originalName);
        boolean isPdf = "pdf".equals(ext);
        BankingFileKind kind = isPdf ? BankingFileKind.PDF : BankingFileKind.DATA;

        String relative;
        try {
            relative = writeToImportTree(root, uid, institutionId, originalName, raw);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save upload", e);
        }

        BankingParseOutcome parse = BankingFormatParser.parse(raw, originalName);

        BankingImportFile fileEntity = new BankingImportFile();
        fileEntity.setOwnerUserId(uid);
        fileEntity.setInstitution(inst);
        fileEntity.setFileKind(kind);
        fileEntity.setOriginalFilename(originalName);
        fileEntity.setContentType(multipart.getContentType());
        fileEntity.setSha256Hex(sha);
        fileEntity.setStoredRelativePath(relative);
        fileEntity.setSizeBytes(raw.length);
        fileEntity.setSkippedDuplicateFile(false);

        int inserted = 0;
        int skippedDup = 0;
        StringBuilder note = new StringBuilder();
        if (parse.note() != null && !parse.note().isBlank()) {
            note.append(parse.note().trim());
        }

        if (!isPdf) {
            List<BankingParsedRow> rows = parse.rows();
            List<String> hashes = new ArrayList<>(rows.size());
            for (BankingParsedRow r : rows) {
                hashes.add(BankingHashUtil.transactionDedupeHex(
                        uid, institutionId, r.date(), r.amount(), r.description()));
            }
            Set<String> existingDb = lookupExistingHashes(uid, hashes);
            Set<String> seen = new HashSet<>();
            fileEntity = importFileRepository.save(fileEntity);
            for (int i = 0; i < rows.size(); i++) {
                BankingParsedRow r = rows.get(i);
                String h = hashes.get(i);
                if (seen.contains(h)) {
                    skippedDup++;
                    continue;
                }
                seen.add(h);
                if (existingDb.contains(h)) {
                    skippedDup++;
                    continue;
                }
                BankingTransaction t = new BankingTransaction();
                t.setOwnerUserId(uid);
                t.setInstitution(inst);
                t.setImportFile(fileEntity);
                t.setTxnDate(r.date());
                t.setAmount(r.amount());
                t.setDescription(r.description() == null ? "" : r.description());
                t.setDedupeHash(h);
                transactionRepository.save(t);
                existingDb.add(h);
                inserted++;
            }
            if (rows.isEmpty() && note.isEmpty()) {
                note.append("No transactions extracted; file stored for troubleshooting.");
            }
        } else {
            fileEntity = importFileRepository.save(fileEntity);
            if (note.isEmpty()) {
                note.append("PDF stored.");
            }
        }

        fileEntity.setRowsInserted(inserted);
        fileEntity.setRowsSkippedDuplicate(skippedDup);
        fileEntity.setParseNote(note.isEmpty() ? null : note.toString());
        fileEntity = importFileRepository.save(fileEntity);

        log.info(
                "Banking import user={} institution={} file={} kind={} rowsInserted={} rowsSkippedDup={}",
                uid,
                institutionId,
                originalName,
                kind,
                inserted,
                skippedDup);

        return new BankingImportResultDto(
                true, false, toFileDto(fileEntity), isPdf ? "PDF uploaded." : "Import completed.");
    }

    @Transactional(readOnly = true)
    public BankingLedgerDto ledger(BankingLedgerRange range, int year, Integer month, Integer quarter, Long institutionId) {
        long uid = currentUserService.requireUserId();
        String importRoot = bankingProps.importDirectory();
        boolean configured = !importRoot.isBlank();
        LocalDate from;
        LocalDate to;
        String label;
        switch (range) {
            case MONTH -> {
                int m = month == null ? 1 : month;
                YearMonth ym = YearMonth.of(year, m);
                from = ym.atDay(1);
                to = ym.atEndOfMonth();
                label = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
            }
            case QUARTER -> {
                int q = quarter == null ? 1 : quarter;
                if (q < 1) {
                    q = 1;
                }
                if (q > 4) {
                    q = 4;
                }
                int startMonth = (q - 1) * 3 + 1;
                from = LocalDate.of(year, startMonth, 1);
                to = from.plusMonths(3).minusDays(1);
                label = "Q" + q + " " + year;
            }
            case YEAR -> {
                from = LocalDate.of(year, 1, 1);
                to = LocalDate.of(year, 12, 31);
                label = Integer.toString(year);
            }
            default -> throw new IllegalStateException();
        }

        Instant fromInst = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<BankingInstitutionDto> institutions =
                institutionRepository.findByOwnerUserIdOrderByNameAsc(uid).stream()
                        .map(BankingService::toInstitutionDto)
                        .toList();

        List<BankingTransaction> txns =
                transactionRepository.listInRange(uid, from, to, institutionId);
        List<BankingTransactionDto> txnDtos = txns.stream().map(BankingService::toTxnDto).toList();

        List<BankingImportFile> files =
                importFileRepository.listUploadedInRange(uid, fromInst, toExclusive, institutionId);
        List<BankingImportFileDto> fileDtos = files.stream().map(BankingService::toFileDto).toList();

        return new BankingLedgerDto(
                configured,
                configured ? Path.of(importRoot).toAbsolutePath().normalize().toString() : "",
                label,
                institutions,
                txnDtos,
                fileDtos);
    }

    @Transactional(readOnly = true)
    public BankingFileContent readFile(long fileId) {
        long uid = currentUserService.requireUserId();
        String importRoot = bankingProps.importDirectory();
        if (importRoot.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import directory not configured");
        }
        BankingImportFile f = importFileRepository
                .findByIdAndOwnerUserId(fileId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        Path root = Path.of(importRoot).toAbsolutePath().normalize();
        Path abs = root.resolve(f.getStoredRelativePath()).normalize();
        if (!abs.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stored path");
        }
        if (!Files.isRegularFile(abs)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored file missing on disk");
        }
        try {
            byte[] body = Files.readAllBytes(abs);
            String ct = f.getContentType() != null && !f.getContentType().isBlank()
                    ? f.getContentType()
                    : "application/octet-stream";
            return new BankingFileContent(f.getOriginalFilename(), ct, body);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Read failed", e);
        }
    }

    public record BankingFileContent(String filename, String contentType, byte[] body) {}

    private Set<String> lookupExistingHashes(long uid, List<String> hashes) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < hashes.size(); i += HASH_LOOKUP_CHUNK) {
            int end = Math.min(i + HASH_LOOKUP_CHUNK, hashes.size());
            List<String> chunk = hashes.subList(i, end);
            if (!chunk.isEmpty()) {
                out.addAll(transactionRepository.findExistingDedupeHashes(uid, chunk));
            }
        }
        return out;
    }

    private static String writeToImportTree(Path root, long userId, long institutionId, String originalName, byte[] raw)
            throws IOException {
        Path userDir = root.resolve(Long.toString(userId)).resolve(Long.toString(institutionId));
        Files.createDirectories(userDir);
        String safe = sanitizeFilename(originalName);
        String unique = UUID.randomUUID() + "_" + safe;
        Path dest = userDir.resolve(unique).normalize();
        if (!dest.startsWith(root)) {
            throw new IOException("Path escapes import root");
        }
        Files.write(dest, raw);
        return root.relativize(dest).toString().replace('\\', '/');
    }

    private static String sanitizeFilename(String name) {
        String base = name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0 && slash < base.length() - 1) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (cleaned.isBlank()) {
            cleaned = "upload.bin";
        }
        if (cleaned.length() > 180) {
            cleaned = cleaned.substring(0, 180);
        }
        return cleaned;
    }

    private static BankingInstitutionDto toInstitutionDto(BankingInstitution e) {
        return new BankingInstitutionDto(e.getId(), e.getName());
    }

    private static BankingImportFileDto toFileDto(BankingImportFile f) {
        BankingInstitution inst = f.getInstitution();
        return new BankingImportFileDto(
                f.getId(),
                inst.getId(),
                inst.getName(),
                f.getFileKind().name(),
                f.getOriginalFilename(),
                f.getContentType(),
                f.getSha256Hex(),
                f.getSizeBytes(),
                f.isSkippedDuplicateFile(),
                f.getRowsInserted(),
                f.getRowsSkippedDuplicate(),
                f.getParseNote(),
                f.getCreatedAt().toString());
    }

    private static BankingTransactionDto toTxnDto(BankingTransaction t) {
        return new BankingTransactionDto(
                t.getId(),
                t.getInstitution().getId(),
                t.getInstitution().getName(),
                t.getImportFile().getId(),
                t.getTxnDate().toString(),
                t.getAmount(),
                t.getDescription());
    }
}
