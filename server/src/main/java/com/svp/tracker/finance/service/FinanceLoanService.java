package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceLoan;
import com.svp.tracker.finance.dto.FinanceLoanDto;
import com.svp.tracker.finance.dto.FinanceLoanOptionsDto;
import com.svp.tracker.finance.dto.FinanceLoanRequestDto;
import com.svp.tracker.finance.repository.FinanceLoanRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinanceLoanService {

    private final CurrentUserService currentUser;
    private final FinanceLoanRepository loanRepository;
    private final FinanceEntryDocumentService entryDocumentService;

    @Transactional(readOnly = true)
    public FinanceLoanOptionsDto options() {
        return new FinanceLoanOptionsDto(
                FinanceLoanCatalog.loanNatureOptions(), FinanceLoanCatalog.paymentFrequencyOptions());
    }

    @Transactional(readOnly = true)
    public List<FinanceLoanDto> listForCurrentUser() {
        long uid = currentUser.requireUserId();
        return loanRepository.findByOwnerUserIdOrderByInstitutionAscDateAvailedDesc(uid).stream()
                .map(row -> toDto(row, uid))
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanceLoanDto getForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        return toDto(requireOwned(id, uid), uid);
    }

    @Transactional
    public FinanceLoanDto createForCurrentUser(FinanceLoanRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceLoan row = new FinanceLoan();
        row.setOwnerUserId(uid);
        apply(row, req);
        return toDto(loanRepository.save(row), uid);
    }

    @Transactional
    public FinanceLoanDto updateForCurrentUser(long id, FinanceLoanRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceLoan row = requireOwned(id, uid);
        apply(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(loanRepository.save(row), uid);
    }

    @Transactional
    public void deleteForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        requireOwned(id, uid);
        entryDocumentService.deleteAllForEntity(FinanceEntryEntityType.LOAN, id, uid);
        loanRepository.deleteById(id);
    }

    private FinanceLoan requireOwned(long id, long uid) {
        return loanRepository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
    }

    private void apply(FinanceLoan row, FinanceLoanRequestDto req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String institution = clean(req.institution());
        if (institution.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution is required");
        }
        row.setInstitution(institution);
        try {
            row.setLoanNature(FinanceLoanCatalog.normalizeNature(req.loanNature()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        row.setNatureOther(clean(req.natureOther()));
        if (!"OTHER".equals(row.getLoanNature()) && !row.getNatureOther().isBlank()) {
            row.setNatureOther("");
        }
        if ("OTHER".equals(row.getLoanNature()) && row.getNatureOther().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe the loan nature when type is Other");
        }
        row.setDateAvailed(req.dateAvailed());
        row.setDateToCommence(req.dateToCommence());
        row.setCurrentBalance(req.currentBalance());
        row.setInterestRate(req.interestRate());
        row.setPaidSoFar(req.paidSoFar());
        row.setBalanceToPay(req.balanceToPay());
        try {
            row.setPaymentFrequency(FinanceLoanCatalog.normalizeFrequency(req.paymentFrequency()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        row.setNotes(emptyToNull(req.notes()));
    }

    private FinanceLoanDto toDto(FinanceLoan row, long ownerUserId) {
        String natureLabel = FinanceLoanCatalog.natureLabel(row.getLoanNature());
        if ("OTHER".equals(row.getLoanNature()) && row.getNatureOther() != null && !row.getNatureOther().isBlank()) {
            natureLabel = row.getNatureOther();
        }
        int documentCount =
                (int) entryDocumentService.countForEntity(FinanceEntryEntityType.LOAN, row.getId(), ownerUserId);
        return new FinanceLoanDto(
                row.getId(),
                row.getInstitution(),
                row.getLoanNature(),
                natureLabel,
                row.getNatureOther() == null ? "" : row.getNatureOther(),
                row.getDateAvailed(),
                row.getDateToCommence(),
                row.getCurrentBalance(),
                row.getInterestRate(),
                row.getPaidSoFar(),
                row.getBalanceToPay(),
                row.getPaymentFrequency(),
                FinanceLoanCatalog.frequencyLabel(row.getPaymentFrequency()),
                row.getNotes() == null ? "" : row.getNotes(),
                documentCount,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
