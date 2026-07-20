package com.svp.tracker.journal.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.domain.JournalCourse;
import com.svp.tracker.journal.dto.JournalCourseDto;
import com.svp.tracker.journal.dto.JournalCourseWriteRequest;
import com.svp.tracker.journal.repository.JournalCourseRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class JournalCourseService {

    private static final Set<String> STATUSES = Set.of("INTEND", "IN_PROGRESS", "COMPLETED");

    private final JournalCourseRepository repository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<JournalCourseDto> list(String status, String q) {
        long owner = currentUser.requireUserId();
        String statusFilter = normalizeStatus(status);
        String query = normalizeQuery(q);
        String queryLower = query == null ? null : query.toLowerCase();
        return repository.findByOwnerUserIdOrderByUpdatedAtDescIdDesc(owner).stream()
                .filter(c -> statusFilter == null || statusFilter.equals(c.getStatus()))
                .filter(c -> queryLower == null
                        || containsIgnoreCase(c.getTitle(), queryLower)
                        || containsIgnoreCase(c.getProvider(), queryLower))
                .sorted(Comparator.comparingInt((JournalCourse c) -> statusRank(c.getStatus()))
                        .thenComparing(JournalCourse::getUpdatedAt, Comparator.reverseOrder())
                        .thenComparing(JournalCourse::getId, Comparator.reverseOrder()))
                .map(this::toDto)
                .toList();
    }

    private static boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase().contains(queryLower);
    }

    private static int statusRank(String status) {
        if ("IN_PROGRESS".equals(status)) {
            return 0;
        }
        if ("INTEND".equals(status)) {
            return 1;
        }
        return 2;
    }

    @Transactional(readOnly = true)
    public JournalCourseDto get(long id) {
        JournalCourse row = repository.findById(id).orElseThrow(() -> new NotFoundException("Course not found: " + id));
        assertOwner(row.getOwnerUserId());
        return toDto(row);
    }

    @Transactional
    public JournalCourseDto create(JournalCourseWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        JournalCourse row = new JournalCourse();
        row.setOwnerUserId(owner);
        applyWrite(row, req);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return toDto(repository.save(row));
    }

    @Transactional
    public JournalCourseDto update(long id, JournalCourseWriteRequest req) {
        JournalCourse row = repository.findById(id).orElseThrow(() -> new NotFoundException("Course not found: " + id));
        assertOwner(row.getOwnerUserId());
        applyWrite(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(repository.save(row));
    }

    @Transactional
    public void delete(long id) {
        JournalCourse row = repository.findById(id).orElseThrow(() -> new NotFoundException("Course not found: " + id));
        assertOwner(row.getOwnerUserId());
        repository.delete(row);
    }

    private void applyWrite(JournalCourse row, JournalCourseWriteRequest req) {
        row.setTitle(req.title().trim());
        row.setProvider(normalizeNullable(req.provider()));
        row.setStatus(requireStatus(req.status()));
        row.setUrl(normalizeNullable(req.url()));
        row.setNotesMarkdown(req.notesMarkdown() == null ? "" : req.notesMarkdown());
        row.setStartedOn(req.startedOn());
        row.setCompletedOn(req.completedOn());
    }

    private JournalCourseDto toDto(JournalCourse row) {
        return new JournalCourseDto(
                row.getId(),
                row.getTitle(),
                row.getProvider(),
                row.getStatus(),
                row.getUrl(),
                row.getNotesMarkdown(),
                row.getStartedOn(),
                row.getCompletedOn(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private void assertOwner(long ownerUserId) {
        if (ownerUserId != currentUser.requireUserId()) {
            throw new NotFoundException("Course not found");
        }
    }

    private static String requireStatus(String status) {
        if (status == null || !STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid course status");
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
