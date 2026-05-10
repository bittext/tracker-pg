package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementWorkLogEntry;
import com.svp.tracker.management.dto.ManagementWorkLogCalendarDto;
import com.svp.tracker.management.dto.ManagementWorkLogEntryDto;
import com.svp.tracker.management.dto.ManagementWorkLogEntryWriteRequest;
import com.svp.tracker.management.repository.ManagementWorkLogEntryRepository;
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
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementWorkLogService {

    private final ManagementWorkLogEntryRepository repository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementWorkLogEntryDto> listBetween(LocalDate from, LocalDate to) {
        validateRange(from, to);
        long owner = currentUser.requireUserId();
        return repository.findByOwnerUserIdAndEntryDateBetweenOrderByEntryDateDescLoggedAtDesc(owner, from, to).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ManagementWorkLogEntryDto> listForDay(LocalDate date) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        long owner = currentUser.requireUserId();
        return repository.findByOwnerUserIdAndEntryDateOrderByLoggedAtDesc(owner, date).stream()
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
                .findById(id)
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
        return toDto(e);
    }

    @Transactional
    public ManagementWorkLogEntryDto update(long id, ManagementWorkLogEntryWriteRequest req) {
        ManagementWorkLogEntry e = repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Work log entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        e.setEntryDate(req.entryDate());
        e.setSubject(normalizeSubject(req.subject()));
        e.setBody(req.body() == null ? "" : req.body());
        e.setUpdatedAt(Instant.now());
        e = repository.save(e);
        return toDto(e);
    }

    @Transactional
    public void delete(long id) {
        ManagementWorkLogEntry e = repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Work log entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        repository.deleteById(id);
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
        return new ManagementWorkLogEntryDto(
                e.getId(),
                e.getOwnerUserId(),
                e.getEntryDate(),
                e.getLoggedAt(),
                e.getSubject() == null ? "" : e.getSubject(),
                e.getBody() == null ? "" : e.getBody(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
