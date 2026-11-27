package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import com.svp.tracker.finance.dto.RobinhoodCashIoImportDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoImportRequestDto;
import com.svp.tracker.finance.repository.RobinhoodAccountCashIoRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Brings Robinhood transfer history into Inputs / Outputs. Rows already in the ledger (same account, direction and
 * amount, within a week of the transfer's date) are recognized as duplicates and never added twice.
 */
@Service
@RequiredArgsConstructor
public class RobinhoodCashIoImportService {

    static final int DUPLICATE_WINDOW_DAYS = 7;
    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_DUPLICATE = "DUPLICATE";
    private static final int MAX_CSV_CHARS = 5_000_000;

    private final CurrentUserService currentUser;
    private final RobinhoodAccountCashIoRepository repository;
    private final RobinhoodCashIoService cashIoService;
    private final RobinhoodCashIoDailyService cashIoDailyService;

    @Transactional(readOnly = true)
    public RobinhoodCashIoImportDto preview(RobinhoodCashIoImportRequestDto body) {
        return run(body, false);
    }

    @Transactional
    public RobinhoodCashIoImportDto importRows(RobinhoodCashIoImportRequestDto body) {
        return run(body, true);
    }

    private RobinhoodCashIoImportDto run(RobinhoodCashIoImportRequestDto body, boolean commit) {
        if (body == null || body.csv() == null || body.csv().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a CSV file to import.");
        }
        if (body.csv().length() > MAX_CSV_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file is too large to import.");
        }
        LocalDate periodFrom = body.periodFrom();
        LocalDate periodTo = body.periodTo();
        if (periodFrom != null && periodTo != null && periodTo.isBefore(periodFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The period ends before it starts.");
        }
        long uid = currentUser.requireUserId();
        String raw = body.accountSuffix() == null ? "" : body.accountSuffix().trim();
        boolean allAccounts = raw.isEmpty() || raw.equalsIgnoreCase("ALL");
        String suffix = allAccounts ? null : RobinhoodCashIoService.normalizeRequiredSuffix(raw);
        Map<String, String> labels = cashIoService.accountLabelMap(uid);

        RobinhoodCashIoCsvParser.Result parsed =
                RobinhoodCashIoCsvParser.parse(body.csv(), suffix, knownParties(uid), labels);

        // Everything follows the requested date; the period limits which requested dates are taken.
        int ignored = parsed.ignored();
        List<RobinhoodCashIoCsvParser.Row> inPeriod = new ArrayList<>();
        for (RobinhoodCashIoCsvParser.Row r : parsed.rows()) {
            LocalDate d = r.requestedDate();
            if ((periodFrom != null && d.isBefore(periodFrom)) || (periodTo != null && d.isAfter(periodTo))) {
                ignored++;
            } else {
                inPeriod.add(r);
            }
        }
        List<RobinhoodCashIoCsvParser.Row> sorted = new ArrayList<>(inPeriod);
        sorted.sort(Comparator.comparing(RobinhoodCashIoCsvParser.Row::requestedDate));

        Map<String, List<RobinhoodCashIoCsvParser.Row>> byAccount = new LinkedHashMap<>();
        for (RobinhoodCashIoCsvParser.Row r : sorted) {
            byAccount.computeIfAbsent(r.accountSuffix(), k -> new ArrayList<>()).add(r);
        }
        Map<RobinhoodCashIoCsvParser.Row, Boolean> freshByRow = new IdentityHashMap<>();
        for (Map.Entry<String, List<RobinhoodCashIoCsvParser.Row>> e : byAccount.entrySet()) {
            List<RobinhoodAccountCashIo> existing = existingAround(uid, e.getKey(), e.getValue());
            List<Boolean> isNew = classify(e.getValue(), existing, true);
            for (int i = 0; i < e.getValue().size(); i++) {
                freshByRow.put(e.getValue().get(i), isNew.get(i));
            }
        }

        List<RobinhoodCashIoImportDto.Row> out = new ArrayList<>();
        Map<String, LocalDate> earliestByAccount = new LinkedHashMap<>();
        int newCount = 0;
        int dupCount = 0;
        Instant now = Instant.now();
        for (RobinhoodCashIoCsvParser.Row r : sorted) {
            boolean fresh = freshByRow.get(r);
            String account = r.accountSuffix();
            out.add(new RobinhoodCashIoImportDto.Row(
                    r.requestedDate(),
                    r.requestedDate(),
                    r.completedDate(),
                    account,
                    labels.getOrDefault(account, "••••" + account),
                    r.direction(),
                    r.amount(),
                    r.counterparty(),
                    r.note(),
                    fresh ? STATUS_NEW : STATUS_DUPLICATE));
            if (!fresh) {
                dupCount++;
                continue;
            }
            newCount++;
            earliestByAccount.merge(account, r.requestedDate(), (x, y) -> x.isBefore(y) ? x : y);
            if (commit) {
                RobinhoodAccountCashIo row = new RobinhoodAccountCashIo();
                row.setOwnerUserId(uid);
                row.setAccountSuffix(account);
                row.setActivityDate(r.requestedDate());
                row.setDirection(r.direction());
                row.setAmount(r.amount());
                row.setNote(r.note());
                row.setCounterparty(r.counterparty());
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                repository.save(row);
            }
        }
        if (commit) {
            earliestByAccount.forEach((account, date) -> cashIoDailyService.rebuildAfterLedgerChange(uid, account, date));
        }
        List<RobinhoodCashIoImportDto.Skipped> skipped = parsed.skipped().stream()
                .map(s -> new RobinhoodCashIoImportDto.Skipped(s.source(), s.reason()))
                .toList();
        return new RobinhoodCashIoImportDto(
                parsed.format(),
                suffix,
                periodFrom,
                periodTo,
                out,
                skipped,
                newCount,
                dupCount,
                ignored,
                commit ? newCount : 0);
    }

    /**
     * Marks each row (sorted by date) as new or as a duplicate of an existing ledger entry. Each existing entry can
     * absorb only one imported row, so two real $150 deposits are not collapsed into one.
     */
    static List<Boolean> classify(
            List<RobinhoodCashIoCsvParser.Row> rows, List<RobinhoodAccountCashIo> existing, boolean requested) {
        Set<Integer> used = new HashSet<>();
        List<Boolean> result = new ArrayList<>();
        for (RobinhoodCashIoCsvParser.Row r : rows) {
            int best = -1;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < existing.size(); i++) {
                RobinhoodAccountCashIo e = existing.get(i);
                if (used.contains(i)
                        || !r.direction().equals(e.getDirection())
                        || r.amount().compareTo(e.getAmount()) != 0) {
                    continue;
                }
                long distance = Math.min(
                        Math.abs(ChronoUnit.DAYS.between(e.getActivityDate(), r.dateFor(requested))),
                        Math.abs(ChronoUnit.DAYS.between(e.getActivityDate(), r.dateFor(!requested))));
                if (distance <= DUPLICATE_WINDOW_DAYS && distance < bestDistance) {
                    best = i;
                    bestDistance = distance;
                }
            }
            if (best >= 0) {
                used.add(best);
                result.add(Boolean.FALSE);
            } else {
                result.add(Boolean.TRUE);
            }
        }
        return result;
    }

    private List<RobinhoodAccountCashIo> existingAround(
            long uid, String suffix, List<RobinhoodCashIoCsvParser.Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate from = rows.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.requestedDate(), r.completedDate()))
                .min(Comparator.naturalOrder())
                .orElseThrow()
                .minusDays(DUPLICATE_WINDOW_DAYS);
        LocalDate to = rows.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.requestedDate(), r.completedDate()))
                .max(Comparator.naturalOrder())
                .orElseThrow()
                .plusDays(DUPLICATE_WINDOW_DAYS);
        return repository.findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                uid, suffix, from, to);
    }

    /** Names already used on entries come first, then the standing outside accounts. */
    private List<String> knownParties(long uid) {
        List<String> known = new ArrayList<>(repository.findDistinctCounterparties(uid));
        for (String standing : RobinhoodCashIoService.STANDING_OUTSIDE_PARTIES) {
            if (known.stream().noneMatch(k -> k.toLowerCase(Locale.ROOT).equals(standing.toLowerCase(Locale.ROOT)))) {
                known.add(standing);
            }
        }
        return known;
    }
}
