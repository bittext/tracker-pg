package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementAccount;
import com.svp.tracker.management.dto.ManagementAccountDto;
import com.svp.tracker.management.dto.ManagementAccountImportRequest;
import com.svp.tracker.management.dto.ManagementAccountImportResultDto;
import com.svp.tracker.management.dto.ManagementAccountWriteRequest;
import com.svp.tracker.management.repository.ManagementAccountRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementAccountsService {

    private final ManagementAccountRepository repository;
    private final ManagementAccountsCrypto crypto;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementAccountDto> list() {
        long owner = currentUser.requireUserId();
        return repository.findByOwnerUserIdOrderByFolderAscItemNameAscIdAsc(owner).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ManagementAccountDto create(ManagementAccountWriteRequest req) {
        long owner = currentUser.requireUserId();
        String itemName = nonNullTrim(req.itemName());
        if (itemName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item name is required");
        }
        Instant now = Instant.now();
        ManagementAccount e = new ManagementAccount();
        e.setOwnerUserId(owner);
        applyWrite(e, req);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e = repository.save(e);
        return toDto(e);
    }

    @Transactional
    public ManagementAccountDto update(long id, ManagementAccountWriteRequest req) {
        long owner = currentUser.requireUserId();
        ManagementAccount e = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
        if (nonNullTrim(req.itemName()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item name is required");
        }
        applyWrite(e, req);
        e.setUpdatedAt(Instant.now());
        e = repository.save(e);
        return toDto(e);
    }

    @Transactional
    public void delete(long id) {
        long owner = currentUser.requireUserId();
        ManagementAccount e = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
        repository.delete(e);
    }

    /**
     * Bulk-import entries from a client (e.g. legacy browser localStorage). Idempotent in practice: skips entries
     * whose {@code (folder, item_name)} already exist for the owner (case-insensitive). Used by the web UI to
     * migrate localStorage on first login after upgrading to server-backed storage.
     */
    @Transactional
    public ManagementAccountImportResultDto bulkImport(ManagementAccountImportRequest req) {
        long owner = currentUser.requireUserId();
        if (req == null || req.entries() == null || req.entries().isEmpty()) {
            return new ManagementAccountImportResultDto(0, 0, 0);
        }
        int submitted = req.entries().size();
        int inserted = 0;
        int skipped = 0;
        Instant now = Instant.now();
        for (ManagementAccountWriteRequest w : req.entries()) {
            String itemName = nonNullTrim(w.itemName());
            if (itemName.isEmpty()) {
                skipped++;
                continue;
            }
            String folder = nonNullTrim(w.folder());
            if (repository.existsByOwnerUserIdAndFolderIgnoreCaseAndItemNameIgnoreCase(owner, folder, itemName)) {
                skipped++;
                continue;
            }
            ManagementAccount e = new ManagementAccount();
            e.setOwnerUserId(owner);
            applyWrite(e, w);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            repository.save(e);
            inserted++;
        }
        return new ManagementAccountImportResultDto(submitted, inserted, skipped);
    }

    private void applyWrite(ManagementAccount e, ManagementAccountWriteRequest req) {
        e.setItemName(nonNullTrim(req.itemName()));
        e.setFolder(nonNullTrim(req.folder()));
        e.setUsername(nonNullTrim(req.username()));
        e.setPasswordEnc(crypto.seal(nonNullTrim(req.password())));
        e.setAuthenticatorKeyEnc(crypto.seal(nonNullTrim(req.authenticatorKey())));
        e.setWebsite(nonNullTrim(req.website()));
        e.setNotes(req.notes() == null ? "" : req.notes());
    }

    private ManagementAccountDto toDto(ManagementAccount e) {
        return new ManagementAccountDto(
                e.getId(),
                e.getItemName(),
                e.getFolder(),
                e.getUsername(),
                crypto.open(e.getPasswordEnc()),
                crypto.open(e.getAuthenticatorKeyEnc()),
                e.getWebsite(),
                e.getNotes(),
                e.getCreatedAt().toString(),
                e.getUpdatedAt().toString());
    }

    private static String nonNullTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
