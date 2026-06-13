package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceInsurancePolicy;
import com.svp.tracker.finance.dto.FinanceInsuranceOptionsDto;
import com.svp.tracker.finance.dto.FinanceInsurancePolicyDto;
import com.svp.tracker.finance.dto.FinanceInsurancePolicyRequestDto;
import com.svp.tracker.finance.dto.FinanceInsuranceSummaryDto;
import com.svp.tracker.finance.repository.FinanceInsurancePolicyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinanceInsurancePolicyService {

    private final CurrentUserService currentUser;
    private final FinanceInsurancePolicyRepository policyRepository;
    private final FinanceEntryDocumentService entryDocumentService;

    @Transactional(readOnly = true)
    public FinanceInsuranceOptionsDto options() {
        return new FinanceInsuranceOptionsDto(
                FinanceInsuranceCatalog.policyTypeOptions(), FinanceInsuranceCatalog.premiumFrequencyOptions());
    }

    @Transactional(readOnly = true)
    public FinanceInsuranceSummaryDto summaryForCurrentUser() {
        long uid = currentUser.requireUserId();
        List<FinanceInsurancePolicy> policies =
                policyRepository.findByOwnerUserIdOrderByCoverageEndDateAscCarrierAsc(uid);
        int dueSoon = 0;
        int expired = 0;
        BigDecimal totalAnnual = BigDecimal.ZERO;
        boolean hasAnnual = false;
        for (FinanceInsurancePolicy p : policies) {
            String status = FinanceInsuranceRenewal.renewalStatus(p.getCoverageEndDate(), safeReminderDays(p));
            if ("DUE_SOON".equals(status)) {
                dueSoon++;
            } else if ("EXPIRED".equals(status)) {
                expired++;
            }
            BigDecimal annual = FinanceInsuranceCatalog.annualizedPremium(p.getPremiumAmount(), p.getPremiumFrequency());
            if (annual != null) {
                hasAnnual = true;
                totalAnnual = totalAnnual.add(annual);
            }
        }
        return new FinanceInsuranceSummaryDto(
                policies.size(), dueSoon, expired, hasAnnual ? totalAnnual : null);
    }

    @Transactional(readOnly = true)
    public List<FinanceInsurancePolicyDto> listForCurrentUser() {
        long uid = currentUser.requireUserId();
        return policyRepository.findByOwnerUserIdOrderByCoverageEndDateAscCarrierAsc(uid).stream()
                .map(row -> toDto(row, uid))
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanceInsurancePolicyDto getForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        return toDto(requireOwned(id, uid), uid);
    }

    @Transactional
    public FinanceInsurancePolicyDto createForCurrentUser(FinanceInsurancePolicyRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceInsurancePolicy row = new FinanceInsurancePolicy();
        row.setOwnerUserId(uid);
        apply(row, req);
        return toDto(policyRepository.save(row), uid);
    }

    @Transactional
    public FinanceInsurancePolicyDto updateForCurrentUser(long id, FinanceInsurancePolicyRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceInsurancePolicy row = requireOwned(id, uid);
        apply(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(policyRepository.save(row), uid);
    }

    @Transactional
    public void deleteForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        requireOwned(id, uid);
        entryDocumentService.deleteAllForEntity(FinanceEntryEntityType.INSURANCE, id, uid);
        policyRepository.deleteById(id);
    }

    private FinanceInsurancePolicy requireOwned(long id, long uid) {
        return policyRepository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Insurance policy not found"));
    }

    private void apply(FinanceInsurancePolicy row, FinanceInsurancePolicyRequestDto req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String carrier = clean(req.carrier());
        if (carrier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carrier is required");
        }
        String coverage = clean(req.coverageDescription());
        if (coverage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coverage description is required");
        }
        row.setCarrier(carrier);
        try {
            row.setPolicyType(FinanceInsuranceCatalog.normalizeType(req.policyType()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        row.setTypeOther(clean(req.typeOther()));
        if (!"OTHER".equals(row.getPolicyType()) && !row.getTypeOther().isBlank()) {
            row.setTypeOther("");
        }
        if ("OTHER".equals(row.getPolicyType()) && row.getTypeOther().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe the policy type when Other is selected");
        }
        row.setPolicyNumber(emptyToNull(req.policyNumber()));
        row.setCoverageDescription(coverage);
        row.setPremiumAmount(req.premiumAmount());
        try {
            row.setPremiumFrequency(FinanceInsuranceCatalog.normalizeFrequency(req.premiumFrequency()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        row.setCoverageStartDate(req.coverageStartDate());
        row.setCoverageEndDate(req.coverageEndDate());
        row.setRenewalReminderDays(safeReminderDays(req.renewalReminderDays()));
        row.setNotes(emptyToNull(req.notes()));
    }

    private FinanceInsurancePolicyDto toDto(FinanceInsurancePolicy row, long ownerUserId) {
        String typeLabel = FinanceInsuranceCatalog.typeLabel(row.getPolicyType());
        if ("OTHER".equals(row.getPolicyType()) && row.getTypeOther() != null && !row.getTypeOther().isBlank()) {
            typeLabel = row.getTypeOther();
        }
        int reminderDays = safeReminderDays(row);
        String renewalStatus = FinanceInsuranceRenewal.renewalStatus(row.getCoverageEndDate(), reminderDays);
        int documentCount =
                (int) entryDocumentService.countForEntity(FinanceEntryEntityType.INSURANCE, row.getId(), ownerUserId);
        return new FinanceInsurancePolicyDto(
                row.getId(),
                row.getCarrier(),
                row.getPolicyType(),
                typeLabel,
                row.getTypeOther() == null ? "" : row.getTypeOther(),
                row.getPolicyNumber() == null ? "" : row.getPolicyNumber(),
                row.getCoverageDescription(),
                row.getPremiumAmount(),
                row.getPremiumFrequency(),
                FinanceInsuranceCatalog.frequencyLabel(row.getPremiumFrequency()),
                FinanceInsuranceCatalog.annualizedPremium(row.getPremiumAmount(), row.getPremiumFrequency()),
                row.getCoverageStartDate(),
                row.getCoverageEndDate(),
                reminderDays,
                FinanceInsuranceRenewal.daysUntilRenewal(row.getCoverageEndDate()),
                renewalStatus,
                FinanceInsuranceRenewal.renewalStatusLabel(renewalStatus),
                row.getNotes() == null ? "" : row.getNotes(),
                documentCount,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static int safeReminderDays(FinanceInsurancePolicy row) {
        return safeReminderDays(row.getRenewalReminderDays());
    }

    private static int safeReminderDays(Integer days) {
        if (days == null || days < 0) {
            return 30;
        }
        return Math.min(days, 365);
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
