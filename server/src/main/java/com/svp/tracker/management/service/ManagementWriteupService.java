package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementWriteup;
import com.svp.tracker.management.dto.ManagementWriteupDto;
import com.svp.tracker.management.dto.ManagementWriteupWriteRequest;
import com.svp.tracker.management.repository.ManagementWriteupRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementWriteupService {

    private final ManagementWriteupRepository repository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementWriteupDto> listForYear(int year) {
        validateYear(year);
        long owner = currentUser.requireUserId();
        return repository.findByOwnerUserIdAndYearOrderByUpdatedAtDesc(owner, year).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagementWriteupDto get(long id) {
        ManagementWriteup w = repository.findById(id).orElseThrow(() -> new NotFoundException("Write-up not found: " + id));
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
        return toDto(w);
    }

    @Transactional
    public ManagementWriteupDto update(long id, ManagementWriteupWriteRequest req) {
        ManagementWriteup w = repository.findById(id).orElseThrow(() -> new NotFoundException("Write-up not found: " + id));
        assertOwner(w.getOwnerUserId());
        validateYear(req.year());
        w.setYear(req.year());
        w.setTopic(req.topic().trim());
        w.setHighlight(normalizeNullable(req.highlight()));
        w.setBody(req.body() == null ? "" : req.body());
        w.setUpdatedAt(Instant.now());
        w = repository.save(w);
        return toDto(w);
    }

    @Transactional
    public void delete(long id) {
        ManagementWriteup w = repository.findById(id).orElseThrow(() -> new NotFoundException("Write-up not found: " + id));
        assertOwner(w.getOwnerUserId());
        repository.deleteById(id);
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
        return new ManagementWriteupDto(
                w.getId(),
                w.getOwnerUserId(),
                w.getYear(),
                w.getTopic(),
                w.getHighlight() == null ? "" : w.getHighlight(),
                w.getBody() == null ? "" : w.getBody(),
                w.getCreatedAt().toString(),
                w.getUpdatedAt().toString());
    }
}
