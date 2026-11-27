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
import java.util.LinkedHashSet;
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
    private static final String STATUS_REMOVE = "REMOVE";
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
        List<RobinhoodCashIoCsvParser.Row> sorted = inPeriod(parsed.rows(), periodFrom, periodTo);
        List<RobinhoodCashIoCsvParser.Row> voided = inPeriod(parsed.voided(), periodFrom, periodTo);
        ignored += (parsed.rows().size() - sorted.size()) + (parsed.voided().size() - voided.size());

        Map<String, List<RobinhoodCashIoCsvParser.Row>> byAccount = new LinkedHashMap<>();
        Map<String, List<RobinhoodCashIoCsvParser.Row>> voidedByAccount = new LinkedHashMap<>();
        for (RobinhoodCashIoCsvParser.Row r : sorted) {
            byAccount.computeIfAbsent(r.accountSuffix(), k -> new ArrayList<>()).add(r);
        }
        for (RobinhoodCashIoCsvParser.Row r : voided) {
            voidedByAccount.computeIfAbsent(r.accountSuffix(), k -> new ArrayList<>()).add(r);
        }

        Map<RobinhoodCashIoCsvParser.Row, Boolean> freshByRow = new IdentityHashMap<>();
        Map<RobinhoodCashIoCsvParser.Row, RobinhoodAccountCashIo> removalByRow = new IdentityHashMap<>();
        Set<String> accountsInFile = new LinkedHashSet<>(byAccount.keySet());
        accountsInFile.addAll(voidedByAccount.keySet());
        for (String account : accountsInFile) {
            List<RobinhoodCashIoCsvParser.Row> rows = byAccount.getOrDefault(account, List.of());
            List<RobinhoodCashIoCsvParser.Row> gone = voidedByAccount.getOrDefault(account, List.of());
            List<RobinhoodCashIoCsvParser.Row> both = new ArrayList<>(rows);
            both.addAll(gone);
            List<RobinhoodAccountCashIo> existing = existingAround(uid, account, both);
            Set<Integer> used = new HashSet<>();
            int[] matches = match(rows, existing, true, used);
            for (int i = 0; i < rows.size(); i++) {
                freshByRow.put(rows.get(i), matches[i] < 0);
            }
            // A failed, rejected or expired transfer takes back the entry an earlier import added for it, but only
            // an imported entry, and only one that no completed transfer in this file is using.
            List<RobinhoodAccountCashIo> importedOnly = new ArrayList<>();
            for (int i = 0; i < existing.size(); i++) {
                importedOnly.add(used.contains(i) || !existing.get(i).isImportedFromRobinhood() ? null : existing.get(i));
            }
            int[] voidMatches = match(gone, importedOnly, true, new HashSet<>());
            for (int i = 0; i < gone.size(); i++) {
                if (voidMatches[i] >= 0) {
                    removalByRow.put(gone.get(i), importedOnly.get(voidMatches[i]));
                }
            }
        }

        List<RobinhoodCashIoImportDto.Row> out = new ArrayList<>();
        List<RobinhoodCashIoImportDto.Row> removals = new ArrayList<>();
        Map<String, LocalDate> earliestByAccount = new LinkedHashMap<>();
        int newCount = 0;
        int dupCount = 0;
        Instant now = Instant.now();
        for (RobinhoodCashIoCsvParser.Row r : sorted) {
            boolean fresh = freshByRow.get(r);
            String account = r.accountSuffix();
            out.add(dtoRow(r, labels, fresh ? STATUS_NEW : STATUS_DUPLICATE));
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
                row.setImportedFromRobinhood(true);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                repository.save(row);
            }
        }
        int removeCount = 0;
        for (RobinhoodCashIoCsvParser.Row r : voided) {
            RobinhoodAccountCashIo entry = removalByRow.get(r);
            if (entry == null) {
                continue;
            }
            removeCount++;
            removals.add(dtoRow(r, labels, STATUS_REMOVE));
            earliestByAccount.merge(entry.getAccountSuffix(), entry.getActivityDate(), (x, y) -> x.isBefore(y) ? x : y);
            if (commit) {
                repository.delete(entry);
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
                removals,
                skipped,
                newCount,
                dupCount,
                removeCount,
                ignored,
                commit ? newCount : 0,
                commit ? removeCount : 0);
    }

    private static List<RobinhoodCashIoCsvParser.Row> inPeriod(
            List<RobinhoodCashIoCsvParser.Row> rows, LocalDate from, LocalDate to) {
        List<RobinhoodCashIoCsvParser.Row> kept = new ArrayList<>();
        for (RobinhoodCashIoCsvParser.Row r : rows) {
            LocalDate d = r.requestedDate();
            if (!((from != null && d.isBefore(from)) || (to != null && d.isAfter(to)))) {
                kept.add(r);
            }
        }
        kept.sort(Comparator.comparing(RobinhoodCashIoCsvParser.Row::requestedDate));
        return kept;
    }

    private static RobinhoodCashIoImportDto.Row dtoRow(
            RobinhoodCashIoCsvParser.Row r, Map<String, String> labels, String status) {
        String account = r.accountSuffix();
        return new RobinhoodCashIoImportDto.Row(
                r.requestedDate(),
                r.requestedDate(),
                r.completedDate(),
                account,
                labels.getOrDefault(account, "••••" + account),
                r.direction(),
                r.amount(),
                r.counterparty(),
                r.note(),
                status);
    }

    /**
     * Marks each row (sorted by date) as new or as a duplicate of an existing ledger entry. Each existing entry can
     * absorb only one imported row, so two real $150 deposits are not collapsed into one.
     */
    static List<Boolean> classify(
            List<RobinhoodCashIoCsvParser.Row> rows, List<RobinhoodAccountCashIo> existing, boolean requested) {
        int[] matches = match(rows, existing, requested, new HashSet<>());
        List<Boolean> result = new ArrayList<>();
        for (int m : matches) {
            result.add(m < 0);
        }
        return result;
    }

    /**
     * For each row, the index of the existing entry it matches (same direction and amount, within a week), or -1.
     * Entries in {@code used} are skipped and every match is added to it. Null entries never match.
     */
    static int[] match(
            List<RobinhoodCashIoCsvParser.Row> rows,
            List<RobinhoodAccountCashIo> existing,
            boolean requested,
            Set<Integer> used) {
        int[] result = new int[rows.size()];
        for (int n = 0; n < rows.size(); n++) {
            RobinhoodCashIoCsvParser.Row r = rows.get(n);
            int best = -1;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < existing.size(); i++) {
                RobinhoodAccountCashIo e = existing.get(i);
                if (e == null
                        || used.contains(i)
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
            }
            result[n] = best;
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
