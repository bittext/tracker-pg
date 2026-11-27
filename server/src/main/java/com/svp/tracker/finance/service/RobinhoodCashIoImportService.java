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
        long uid = currentUser.requireUserId();
        String suffix = RobinhoodCashIoService.normalizeRequiredSuffix(body.accountSuffix());
        boolean requested = !"COMPLETED".equalsIgnoreCase(body.dateMode() == null ? "" : body.dateMode().trim());
        String dateMode = requested ? "REQUESTED" : "COMPLETED";

        List<String> known = knownParties(uid);
        RobinhoodCashIoCsvParser.Result parsed = RobinhoodCashIoCsvParser.parse(body.csv(), suffix, known);

        List<RobinhoodCashIoCsvParser.Row> sorted = new ArrayList<>(parsed.rows());
        sorted.sort(Comparator.comparing((RobinhoodCashIoCsvParser.Row r) -> r.dateFor(requested)));
        List<RobinhoodAccountCashIo> existing = existingAround(uid, suffix, sorted);
        List<Boolean> isNew = classify(sorted, existing, requested);

        List<RobinhoodCashIoImportDto.Row> out = new ArrayList<>();
        int newCount = 0;
        int dupCount = 0;
        Instant now = Instant.now();
        LocalDate earliest = null;
        for (int i = 0; i < sorted.size(); i++) {
            RobinhoodCashIoCsvParser.Row r = sorted.get(i);
            boolean fresh = isNew.get(i);
            out.add(new RobinhoodCashIoImportDto.Row(
                    r.dateFor(requested),
                    r.requestedDate(),
                    r.completedDate(),
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
            LocalDate date = r.dateFor(requested);
            if (earliest == null || date.isBefore(earliest)) {
                earliest = date;
            }
            if (commit) {
                RobinhoodAccountCashIo row = new RobinhoodAccountCashIo();
                row.setOwnerUserId(uid);
                row.setAccountSuffix(suffix);
                row.setActivityDate(date);
                row.setDirection(r.direction());
                row.setAmount(r.amount());
                row.setNote(r.note());
                row.setCounterparty(r.counterparty());
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                repository.save(row);
            }
        }
        if (commit && earliest != null) {
            cashIoDailyService.rebuildAfterLedgerChange(uid, suffix, earliest);
        }
        List<RobinhoodCashIoImportDto.Skipped> skipped = parsed.skipped().stream()
                .map(s -> new RobinhoodCashIoImportDto.Skipped(s.source(), s.reason()))
                .toList();
        return new RobinhoodCashIoImportDto(
                parsed.format(),
                suffix,
                dateMode,
                out,
                skipped,
                newCount,
                dupCount,
                parsed.ignored(),
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
