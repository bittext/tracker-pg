package com.svp.tracker.journal.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.domain.JournalBook;
import com.svp.tracker.journal.dto.JournalBookDto;
import com.svp.tracker.journal.dto.JournalBookWriteRequest;
import com.svp.tracker.journal.repository.JournalBookRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class JournalBookService {

    private static final Set<String> STATUSES = Set.of("TO_READ", "READING", "FINISHED");

    private final JournalBookRepository repository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<JournalBookDto> list(String status, String q) {
        long owner = currentUser.requireUserId();
        String statusFilter = normalizeStatus(status);
        String query = normalizeQuery(q);
        return repository.search(owner, statusFilter, query).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public JournalBookDto get(long id) {
        JournalBook row = repository.findById(id).orElseThrow(() -> new NotFoundException("Book not found: " + id));
        assertOwner(row.getOwnerUserId());
        return toDto(row);
    }

    @Transactional
    public JournalBookDto create(JournalBookWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        JournalBook row = new JournalBook();
        row.setOwnerUserId(owner);
        applyWrite(row, req);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return toDto(repository.save(row));
    }

    @Transactional
    public JournalBookDto update(long id, JournalBookWriteRequest req) {
        JournalBook row = repository.findById(id).orElseThrow(() -> new NotFoundException("Book not found: " + id));
        assertOwner(row.getOwnerUserId());
        applyWrite(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(repository.save(row));
    }

    @Transactional
    public void delete(long id) {
        JournalBook row = repository.findById(id).orElseThrow(() -> new NotFoundException("Book not found: " + id));
        assertOwner(row.getOwnerUserId());
        repository.delete(row);
    }

    private void applyWrite(JournalBook row, JournalBookWriteRequest req) {
        row.setTitle(req.title().trim());
        row.setAuthor(normalizeNullable(req.author()));
        row.setStatus(requireStatus(req.status()));
        row.setUrl(normalizeNullable(req.url()));
        row.setNotesMarkdown(req.notesMarkdown() == null ? "" : req.notesMarkdown());
        row.setStartedOn(req.startedOn());
        row.setFinishedOn(req.finishedOn());
        row.setRating(req.rating());
    }

    private JournalBookDto toDto(JournalBook row) {
        return new JournalBookDto(
                row.getId(),
                row.getTitle(),
                row.getAuthor(),
                row.getStatus(),
                row.getUrl(),
                row.getNotesMarkdown(),
                row.getStartedOn(),
                row.getFinishedOn(),
                row.getRating(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private void assertOwner(long ownerUserId) {
        if (ownerUserId != currentUser.requireUserId()) {
            throw new NotFoundException("Book not found");
        }
    }

    private static String requireStatus(String status) {
        if (status == null || !STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid book status");
        }
        return status;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return requireStatus(status.trim());
    }

    private static String normalizeQuery(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
