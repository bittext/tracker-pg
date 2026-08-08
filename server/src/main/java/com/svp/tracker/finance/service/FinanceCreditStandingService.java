package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceCreditStanding;
import com.svp.tracker.finance.dto.FinanceCreditStandingDto;
import com.svp.tracker.finance.dto.FinanceCreditStandingRequestDto;
import com.svp.tracker.finance.repository.FinanceCreditStandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinanceCreditStandingService {

    private final CurrentUserService currentUser;
    private final FinanceCreditStandingRepository repository;
    private final FinanceEntryDocumentService entryDocumentService;

    @Transactional(readOnly = true)
    public FinanceCreditStandingDto getForCurrentUser() {
        long uid = currentUser.requireUserId();
        return repository.findByOwnerUserId(uid).map(this::toDto).orElse(emptyDto());
    }

    @Transactional
    public FinanceCreditStandingDto upsertForCurrentUser(FinanceCreditStandingRequestDto body) {
        long uid = currentUser.requireUserId();
        FinanceCreditStanding row = repository.findByOwnerUserId(uid).orElseGet(() -> {
            FinanceCreditStanding created = new FinanceCreditStanding();
            created.setOwnerUserId(uid);
            return created;
        });
        Integer score = body.score();
        if (score != null && (score < 300 || score > 900)) {
            score = Math.max(300, Math.min(900, score));
        }
        row.setScore(score);
        row.setBureau(trimToNull(body.bureau()));
        row.setReportedAsOf(body.reportedAsOf());
        row.setNotes(body.notes() == null ? null : body.notes().trim());
        row.setAnnualReportPulledAt(body.annualReportPulledAt());
        return toDto(repository.save(row));
    }

    private FinanceCreditStandingDto toDto(FinanceCreditStanding row) {
        int docs = (int) entryDocumentService.countForEntity(
                FinanceEntryEntityType.CREDIT_STANDING, row.getId(), row.getOwnerUserId());
        return new FinanceCreditStandingDto(
                row.getId(),
                row.getScore(),
                row.getBureau() == null ? "" : row.getBureau(),
                row.getReportedAsOf(),
                row.getNotes() == null ? "" : row.getNotes(),
                row.getAnnualReportPulledAt(),
                docs,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static FinanceCreditStandingDto emptyDto() {
        return new FinanceCreditStandingDto(null, null, "", null, "", null, 0, null, null);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
