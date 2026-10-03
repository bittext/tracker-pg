package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementAutoPayment;
import com.svp.tracker.management.domain.ManagementAutoPaymentFrequency;
import com.svp.tracker.management.domain.ManagementAutoPaymentMethod;
import com.svp.tracker.management.domain.ManagementAutoPaymentStatus;
import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.dto.ManagementAutoPaymentDto;
import com.svp.tracker.management.dto.ManagementAutoPaymentWriteRequest;
import com.svp.tracker.management.repository.ManagementAutoPaymentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementAutoPaymentsService {

    private static final ZoneId OWNER_ZONE = ZoneId.of("America/Chicago");

    private final ManagementAutoPaymentRepository repository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementAutoPaymentDto> list(ManagementDesk desk) {
        long owner = currentUser.requireUserId();
        LocalDate today = LocalDate.now(OWNER_ZONE);
        return repository
                .findByOwnerUserIdAndDeskOrderByNameAscIdAsc(owner, resolveDesk(desk))
                .stream()
                .map(row -> toDto(row, today))
                .toList();
    }

    @Transactional
    public ManagementAutoPaymentDto create(ManagementAutoPaymentWriteRequest req, ManagementDesk desk) {
        long owner = currentUser.requireUserId();
        requireName(req);
        Instant now = Instant.now();
        ManagementAutoPayment row = new ManagementAutoPayment();
        row.setOwnerUserId(owner);
        row.setDesk(resolveDesk(desk));
        applyWrite(row, req);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return toDto(repository.save(row), LocalDate.now(OWNER_ZONE));
    }

    @Transactional
    public ManagementAutoPaymentDto update(long id, ManagementAutoPaymentWriteRequest req) {
        long owner = currentUser.requireUserId();
        requireName(req);
        ManagementAutoPayment row = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Auto payment not found: " + id));
        applyWrite(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(repository.save(row), LocalDate.now(OWNER_ZONE));
    }

    @Transactional
    public void delete(long id) {
        long owner = currentUser.requireUserId();
        ManagementAutoPayment row = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Auto payment not found: " + id));
        repository.delete(row);
    }

    private void applyWrite(ManagementAutoPayment row, ManagementAutoPaymentWriteRequest req) {
        row.setName(trim(req.name()));
        row.setPayee(trim(req.payee()));
        row.setCategory(trim(req.category()));
        row.setPaymentMethod(ManagementAutoPaymentSupport.parseMethod(req.paymentMethod()));
        row.setFrequency(ManagementAutoPaymentSupport.parseFrequency(req.frequency()));
        row.setAmount(req.amount());
        String currency = trim(req.currency());
        row.setCurrency(currency.isEmpty() ? "USD" : currency.toUpperCase());
        row.setStartedOn(req.startedOn());
        row.setNextPaymentOn(req.nextPaymentOn());
        row.setDayOfMonth(req.dayOfMonth());
        row.setEndedOn(req.endedOn());
        row.setStatus(ManagementAutoPaymentSupport.parseStatus(req.status()));
        row.setFundingAccount(trim(req.fundingAccount()));
        row.setConfirmationRef(trim(req.confirmationRef()));
        row.setWebsite(trim(req.website()));
        row.setNotes(req.notes() == null ? "" : req.notes());
    }

    private ManagementAutoPaymentDto toDto(ManagementAutoPayment row, LocalDate today) {
        ManagementAutoPaymentFrequency frequency = row.getFrequency();
        ManagementAutoPaymentMethod method = row.getPaymentMethod();
        ManagementAutoPaymentStatus status = row.getStatus();
        LocalDate next = ManagementAutoPaymentSupport.nextDebit(
                row.getStartedOn(),
                row.getNextPaymentOn(),
                row.getDayOfMonth(),
                frequency,
                status,
                today);
        return new ManagementAutoPaymentDto(
                row.getId(),
                row.getName(),
                row.getPayee(),
                row.getCategory(),
                method.name(),
                frequency.name(),
                row.getAmount(),
                row.getCurrency(),
                row.getStartedOn(),
                row.getNextPaymentOn(),
                row.getDayOfMonth(),
                row.getEndedOn(),
                status.name(),
                row.getFundingAccount(),
                row.getConfirmationRef(),
                row.getWebsite(),
                row.getNotes(),
                next,
                ManagementAutoPaymentSupport.daysUntil(next, today),
                row.getCreatedAt().toString(),
                row.getUpdatedAt().toString());
    }

    private static void requireName(ManagementAutoPaymentWriteRequest req) {
        if (req == null || trim(req.name()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
    }

    private static ManagementDesk resolveDesk(ManagementDesk desk) {
        return desk == null ? ManagementDesk.LIFE : desk;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
