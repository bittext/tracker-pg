package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.management.domain.ManagementDayOneLog;
import com.svp.tracker.management.dto.ManagementDayOneLogDto;
import com.svp.tracker.management.dto.ManagementDayOneLogWriteRequest;
import com.svp.tracker.management.repo.ManagementDayOneLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementDayOneService {

    private final ManagementDayOneLogRepository repo;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementDayOneLogDto> listMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<ManagementDayOneLog> rows =
                currentUser.isAdmin()
                        ? repo.findByLoggedOnBetweenOrderByLoggedOnDesc(from, to)
                        : repo.findByOwnerUserIdAndLoggedOnBetweenOrderByLoggedOnDesc(
                                currentUser.requireUserId(), from, to);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public ManagementDayOneLogDto upsert(ManagementDayOneLogWriteRequest req) {
        Long owner = currentUser.requireUserId();
        LocalDate day = req.getLoggedOn();
        String text = req.getEntryText().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryText must not be blank");
        }
        Instant now = Instant.now();
        Optional<ManagementDayOneLog> existing = repo.findByOwnerUserIdAndLoggedOn(owner, day);
        if (existing.isPresent()) {
            ManagementDayOneLog e = existing.get();
            assertRowAccess(e.getOwnerUserId());
            e.setEntryText(text);
            e.setUpdatedAt(now);
            return toDto(repo.save(e));
        }
        ManagementDayOneLog e = new ManagementDayOneLog();
        e.setOwnerUserId(owner);
        e.setLoggedOn(day);
        e.setEntryText(text);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return toDto(repo.save(e));
    }

    @Transactional
    public void delete(long id) {
        ManagementDayOneLog e =
                repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(e.getOwnerUserId());
        repo.delete(e);
    }

    private void assertRowAccess(Long ownerUserId) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private ManagementDayOneLogDto toDto(ManagementDayOneLog e) {
        return ManagementDayOneLogDto.builder()
                .id(e.getId())
                .loggedOn(e.getLoggedOn())
                .entryText(e.getEntryText())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
