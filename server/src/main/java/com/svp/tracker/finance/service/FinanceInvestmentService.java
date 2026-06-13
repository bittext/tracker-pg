package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceInvestment;
import com.svp.tracker.finance.dto.FinanceInvestmentDto;
import com.svp.tracker.finance.dto.FinanceInvestmentOptionsDto;
import com.svp.tracker.finance.dto.FinanceInvestmentRequestDto;
import com.svp.tracker.finance.repository.FinanceInvestmentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinanceInvestmentService {

    private final CurrentUserService currentUser;
    private final FinanceInvestmentRepository investmentRepository;

    @Transactional(readOnly = true)
    public FinanceInvestmentOptionsDto options() {
        return new FinanceInvestmentOptionsDto(FinanceInvestmentCatalog.investmentTypeOptions());
    }

    @Transactional(readOnly = true)
    public List<FinanceInvestmentDto> listForCurrentUser() {
        long uid = currentUser.requireUserId();
        return investmentRepository.findByOwnerUserIdOrderByInstitutionAscNameAsc(uid).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanceInvestmentDto getForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        return toDto(requireOwned(id, uid));
    }

    @Transactional
    public FinanceInvestmentDto createForCurrentUser(FinanceInvestmentRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceInvestment row = new FinanceInvestment();
        row.setOwnerUserId(uid);
        apply(row, req);
        return toDto(investmentRepository.save(row));
    }

    @Transactional
    public FinanceInvestmentDto updateForCurrentUser(long id, FinanceInvestmentRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceInvestment row = requireOwned(id, uid);
        apply(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(investmentRepository.save(row));
    }

    @Transactional
    public void deleteForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        investmentRepository.delete(requireOwned(id, uid));
    }

    private FinanceInvestment requireOwned(long id, long uid) {
        return investmentRepository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investment not found"));
    }

    private void apply(FinanceInvestment row, FinanceInvestmentRequestDto req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String institution = clean(req.institution());
        if (institution.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution is required");
        }
        String name = clean(req.name());
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        row.setInstitution(institution);
        row.setName(name);
        try {
            row.setInvestmentType(FinanceInvestmentCatalog.normalizeType(req.investmentType()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        row.setTypeOther(clean(req.typeOther()));
        if (!"OTHER".equals(row.getInvestmentType())) {
            row.setTypeOther("");
        }
        if ("OTHER".equals(row.getInvestmentType()) && row.getTypeOther().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe the investment type when Other is selected");
        }
        String symbol = clean(req.symbol());
        row.setSymbol(symbol.isBlank() ? null : symbol.toUpperCase(Locale.ROOT));
        row.setDateAcquired(req.dateAcquired());
        row.setQuantity(req.quantity());
        row.setCostBasis(req.costBasis());
        row.setCurrentValue(req.currentValue());
        row.setNotes(emptyToNull(req.notes()));
    }

    private FinanceInvestmentDto toDto(FinanceInvestment row) {
        String typeLabel = FinanceInvestmentCatalog.typeLabel(row.getInvestmentType());
        if ("OTHER".equals(row.getInvestmentType()) && row.getTypeOther() != null && !row.getTypeOther().isBlank()) {
            typeLabel = row.getTypeOther();
        }
        return new FinanceInvestmentDto(
                row.getId(),
                row.getInstitution(),
                row.getInvestmentType(),
                typeLabel,
                row.getTypeOther() == null ? "" : row.getTypeOther(),
                row.getSymbol() == null ? "" : row.getSymbol(),
                row.getName(),
                row.getDateAcquired(),
                row.getQuantity(),
                row.getCostBasis(),
                row.getCurrentValue(),
                computeGainLoss(row.getCostBasis(), row.getCurrentValue()),
                row.getNotes() == null ? "" : row.getNotes(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static BigDecimal computeGainLoss(BigDecimal costBasis, BigDecimal currentValue) {
        if (costBasis == null || currentValue == null) {
            return null;
        }
        return currentValue.subtract(costBasis);
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
