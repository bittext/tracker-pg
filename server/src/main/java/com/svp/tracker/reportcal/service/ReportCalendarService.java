package com.svp.tracker.reportcal.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.reportcal.domain.ReportCalendarEntry;
import com.svp.tracker.reportcal.domain.ReportCalendarType;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryDto;
import com.svp.tracker.reportcal.dto.ReportCalendarEntryWriteDto;
import com.svp.tracker.reportcal.repository.ReportCalendarEntryRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportCalendarService {

    private final ReportCalendarEntryRepository repository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ReportCalendarEntryDto> listInRange(LocalDate from, LocalDate to, ReportCalendarType type) {
        long uid = currentUser.requireUserId();
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to before from");
        }
        return repository
                .findByOwnerUserIdAndCalendarTypeAndEntryDateBetweenOrderByEntryDateAscIdAsc(uid, type, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ReportCalendarEntryDto create(ReportCalendarEntryWriteDto body) {
        long uid = currentUser.requireUserId();
        String title = trimToNull(body.getTitle());
        String text = trimToNull(body.getBody());
        if (title == null && text == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title or information is required");
        }
        ReportCalendarEntry e = new ReportCalendarEntry();
        e.setOwnerUserId(uid);
        e.setEntryDate(body.getEntryDate());
        e.setCalendarType(body.getCalendarType());
        e.setTitle(title);
        e.setBody(text);
        return toDto(repository.save(e));
    }

    @Transactional
    public ReportCalendarEntryDto update(long id, ReportCalendarEntryWriteDto body) {
        ReportCalendarEntry e = repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
        assertRowAccess(e.getOwnerUserId());
        String title = trimToNull(body.getTitle());
        String text = trimToNull(body.getBody());
        if (title == null && text == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title or information is required");
        }
        e.setEntryDate(body.getEntryDate());
        e.setCalendarType(body.getCalendarType());
        e.setTitle(title);
        e.setBody(text);
        return toDto(repository.save(e));
    }

    @Transactional
    public void delete(long id) {
        ReportCalendarEntry e = repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
        assertRowAccess(e.getOwnerUserId());
        repository.delete(e);
    }

    private void assertRowAccess(Long ownerUserId) {
        if (!java.util.Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ReportCalendarEntryDto toDto(ReportCalendarEntry e) {
        return ReportCalendarEntryDto.builder()
                .id(e.getId())
                .entryDate(e.getEntryDate())
                .calendarType(e.getCalendarType())
                .title(e.getTitle())
                .body(e.getBody())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
