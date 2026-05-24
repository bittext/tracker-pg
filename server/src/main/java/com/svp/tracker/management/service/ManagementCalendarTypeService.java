package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementCalendarType;
import com.svp.tracker.management.dto.ManagementCalendarTypeWriteRequest;
import com.svp.tracker.management.repository.ManagementCalendarTypeRepository;
import com.svp.tracker.reportcal.repository.ReportCalendarEntryRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementCalendarTypeService {

    private final ManagementCalendarTypeRepository calendarTypeRepository;
    private final ReportCalendarEntryRepository reportCalendarEntryRepository;
    private final CurrentUserService currentUser;

    private static final List<DefaultCalendarType> DEFAULT_CALENDAR_TYPES = List.of(
            new DefaultCalendarType("BIRTHDAY", "Birthday", 0),
            new DefaultCalendarType("WORK", "Work", 1),
            new DefaultCalendarType("PERSONAL", "Personal", 2),
            new DefaultCalendarType("TRADES", "Trades", 3),
            new DefaultCalendarType("BANKING", "Banking", 4),
            new DefaultCalendarType("PAYMENTS", "Payments", 5),
            new DefaultCalendarType("OPINION_STRATEGIES", "Opinion & strategies", 6));

    private record DefaultCalendarType(String code, String label, int sortIndex) {}

    @Transactional
    public List<ManagementCalendarType> listForCurrentUser() {
        long ownerId = currentUser.requireUserId();
        ensureDefaultCalendarTypes(ownerId);
        return calendarTypeRepository.findByOwnerUserIdOrderBySortIndexAscIdAsc(ownerId);
    }

    @Transactional
    public ManagementCalendarType create(ManagementCalendarTypeWriteRequest req) {
        String code = req.getCode().trim().toUpperCase(Locale.ROOT);
        Long ownerId = currentUser.requireUserId();
        ensureDefaultCalendarTypes(ownerId);
        if (calendarTypeRepository.existsByOwnerUserIdAndCode(ownerId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A calendar type with that code already exists");
        }
        ManagementCalendarType row = new ManagementCalendarType();
        row.setOwnerUserId(ownerId);
        row.setCode(code);
        row.setLabel(req.getLabel().trim());
        row.setSortIndex(req.getSortIndex() != null ? req.getSortIndex() : 0);
        return calendarTypeRepository.save(row);
    }

    @Transactional
    public void delete(Long id) {
        ManagementCalendarType row = calendarTypeRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Calendar type not found: " + id));
        assertRowAccess(row.getOwnerUserId());
        if (reportCalendarEntryRepository.existsByOwnerUserIdAndCalendarType(row.getOwnerUserId(), row.getCode())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Calendar entries use this type; remove or reassign them first");
        }
        calendarTypeRepository.deleteById(id);
    }

    @Transactional
    public void ensureDefaultCalendarTypes(long ownerUserId) {
        if (calendarTypeRepository.countByOwnerUserId(ownerUserId) > 0) {
            return;
        }
        for (DefaultCalendarType d : DEFAULT_CALENDAR_TYPES) {
            ManagementCalendarType row = new ManagementCalendarType();
            row.setOwnerUserId(ownerUserId);
            row.setCode(d.code());
            row.setLabel(d.label());
            row.setSortIndex(d.sortIndex());
            calendarTypeRepository.save(row);
        }
    }

    public boolean isProvisioned(long ownerUserId, String code) {
        return calendarTypeRepository.existsByOwnerUserIdAndCode(ownerUserId, code);
    }

    public String assertValidForUser(long ownerUserId, String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "calendarType is required");
        }
        String code = rawType.trim().toUpperCase(Locale.ROOT);
        ensureDefaultCalendarTypes(ownerUserId);
        if (!isProvisioned(ownerUserId, code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown calendar type: " + code);
        }
        return code;
    }

    private void assertRowAccess(Long ownerUserId) {
        if (ownerUserId == null || !Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new NotFoundException("Calendar type not found");
        }
    }
}
