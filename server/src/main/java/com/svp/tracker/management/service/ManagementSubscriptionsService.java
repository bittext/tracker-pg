package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.domain.ManagementSubscription;
import com.svp.tracker.management.domain.ManagementSubscriptionBillingCycle;
import com.svp.tracker.management.domain.ManagementSubscriptionStatus;
import com.svp.tracker.management.dto.ManagementSubscriptionDto;
import com.svp.tracker.management.dto.ManagementSubscriptionWriteRequest;
import com.svp.tracker.management.repository.ManagementSubscriptionRepository;
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
public class ManagementSubscriptionsService {

    private static final ZoneId OWNER_ZONE = ZoneId.of("America/Chicago");

    private final ManagementSubscriptionRepository repository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<ManagementSubscriptionDto> list(ManagementDesk desk) {
        long owner = currentUser.requireUserId();
        LocalDate today = LocalDate.now(OWNER_ZONE);
        return repository
                .findByOwnerUserIdAndDeskOrderByNameAscIdAsc(owner, resolveDesk(desk))
                .stream()
                .map(row -> toDto(row, today))
                .toList();
    }

    @Transactional
    public ManagementSubscriptionDto create(ManagementSubscriptionWriteRequest req, ManagementDesk desk) {
        long owner = currentUser.requireUserId();
        requireName(req);
        Instant now = Instant.now();
        ManagementSubscription row = new ManagementSubscription();
        row.setOwnerUserId(owner);
        row.setDesk(resolveDesk(desk));
        applyWrite(row, req);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return toDto(repository.save(row), LocalDate.now(OWNER_ZONE));
    }

    @Transactional
    public ManagementSubscriptionDto update(long id, ManagementSubscriptionWriteRequest req) {
        long owner = currentUser.requireUserId();
        requireName(req);
        ManagementSubscription row = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
        applyWrite(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(repository.save(row), LocalDate.now(OWNER_ZONE));
    }

    @Transactional
    public void delete(long id) {
        long owner = currentUser.requireUserId();
        ManagementSubscription row = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
        repository.delete(row);
    }

    private void applyWrite(ManagementSubscription row, ManagementSubscriptionWriteRequest req) {
        row.setName(trim(req.name()));
        row.setVendor(trim(req.vendor()));
        row.setCategory(trim(req.category()));
        row.setPlan(trim(req.plan()));
        row.setBillingCycle(ManagementSubscriptionSupport.parseCycle(req.billingCycle()));
        row.setAmount(req.amount());
        String currency = trim(req.currency());
        row.setCurrency(currency.isEmpty() ? "USD" : currency.toUpperCase());
        row.setEnrolledOn(req.enrolledOn());
        row.setRenewsOn(req.renewsOn());
        row.setTrialEndsOn(req.trialEndsOn());
        row.setCancelledOn(req.cancelledOn());
        row.setStatus(ManagementSubscriptionSupport.parseStatus(req.status()));
        row.setAutoRenew(req.autoRenew() == null || req.autoRenew());
        row.setWebsite(trim(req.website()));
        row.setAccountEmail(trim(req.accountEmail()));
        row.setNotes(req.notes() == null ? "" : req.notes());
    }

    private ManagementSubscriptionDto toDto(ManagementSubscription row, LocalDate today) {
        ManagementSubscriptionBillingCycle cycle = row.getBillingCycle();
        ManagementSubscriptionStatus status = row.getStatus();
        LocalDate next = ManagementSubscriptionSupport.nextRenewal(
                row.getEnrolledOn(), row.getRenewsOn(), cycle, status, row.isAutoRenew(), today);
        LocalDate refundEnds = ManagementSubscriptionSupport.refundWindowEndsOn(row.getEnrolledOn());
        return new ManagementSubscriptionDto(
                row.getId(),
                row.getName(),
                row.getVendor(),
                row.getCategory(),
                row.getPlan(),
                cycle.name(),
                row.getAmount(),
                row.getCurrency(),
                row.getEnrolledOn(),
                row.getRenewsOn(),
                row.getTrialEndsOn(),
                row.getCancelledOn(),
                status.name(),
                row.isAutoRenew(),
                row.getWebsite(),
                row.getAccountEmail(),
                row.getNotes(),
                next,
                ManagementSubscriptionSupport.daysUntil(next, today),
                ManagementSubscriptionSupport.refundWindowOpen(row.getEnrolledOn(), status, today),
                refundEnds,
                row.getCreatedAt().toString(),
                row.getUpdatedAt().toString());
    }

    private static void requireName(ManagementSubscriptionWriteRequest req) {
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
