package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import com.svp.tracker.finance.dto.RobinhoodCashIoAccountDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoCalendarDayDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoCalendarDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoEntryDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoLedgerDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoRequestDto;
import com.svp.tracker.finance.dto.RobinhoodRhOwnedAccountsDto;
import com.svp.tracker.finance.repository.RobinhoodAccountCashIoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RobinhoodCashIoService {

    private static final String DIR_IN = "IN";
    private static final String DIR_OUT = "OUT";
    private static final int MAX_PARTY_LENGTH = 120;

    /** Outside accounts offered as From / To choices before any entry has used them. */
    static final List<String> STANDING_OUTSIDE_PARTIES =
            List.of("Chase", "Robinhood Coastal Banking ••••6310", "Robinhood Savings ••••3848");

    private final CurrentUserService currentUser;
    private final RobinhoodAccountCashIoRepository repository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final RobinhoodCashIoDailyService cashIoDailyService;

    @Transactional(readOnly = true)
    public List<RobinhoodCashIoAccountDto> listAccounts() {
        List<RobinhoodCashIoAccountDto> out = listAccountsFor(currentUser.requireUserId());
        out.sort(Comparator.comparing(RobinhoodCashIoAccountDto::label));
        return out;
    }

    @Transactional(readOnly = true)
    public RobinhoodCashIoLedgerDto ledger(
            int year, Integer month, String accountSuffix, String origin, String receiver) {
        long uid = currentUser.requireUserId();
        DateWindow window = resolveWindow(year, month);
        String suffix = normalizeOptionalSuffix(accountSuffix);
        Map<String, String> labels = accountLabelMap(uid);
        List<RobinhoodAccountCashIo> rows =
                filterByParties(loadRows(uid, suffix, window.from(), window.to()), labels, origin, receiver);
        List<RobinhoodCashIoEntryDto> entries = rows.stream().map(r -> toEntryDto(r, labels)).toList();
        Totals totals = totals(rows);
        return new RobinhoodCashIoLedgerDto(
                year,
                month,
                suffix,
                window.from(),
                window.to(),
                totals.in(),
                totals.out(),
                totals.net(),
                entries);
    }

    @Transactional(readOnly = true)
    public RobinhoodCashIoCalendarDto calendar(
            int year, Integer month, String accountSuffix, String origin, String receiver) {
        long uid = currentUser.requireUserId();
        DateWindow window = resolveWindow(year, month);
        String suffix = normalizeOptionalSuffix(accountSuffix);
        List<RobinhoodAccountCashIo> rows = filterByParties(
                loadRows(uid, suffix, window.from(), window.to()), accountLabelMap(uid), origin, receiver);
        Map<LocalDate, MutableDay> byDay = new LinkedHashMap<>();
        for (RobinhoodAccountCashIo row : rows) {
            MutableDay day = byDay.computeIfAbsent(row.getActivityDate(), d -> new MutableDay());
            day.count++;
            if (DIR_IN.equals(row.getDirection())) {
                day.in = day.in.add(row.getAmount());
            } else {
                day.out = day.out.add(row.getAmount());
            }
        }
        List<RobinhoodCashIoCalendarDayDto> days = new ArrayList<>();
        byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    MutableDay d = e.getValue();
                    days.add(new RobinhoodCashIoCalendarDayDto(
                            e.getKey(),
                            scale(d.in),
                            scale(d.out),
                            scale(d.in.subtract(d.out)),
                            d.count));
                });
        Totals totals = totals(rows);
        return new RobinhoodCashIoCalendarDto(
                year, month, suffix, totals.in(), totals.out(), totals.net(), days);
    }

    @Transactional(readOnly = true)
    public List<String> listParties() {
        long uid = currentUser.requireUserId();
        return mergeParties(
                new ArrayList<>(accountLabelMap(uid).values()), repository.findDistinctCounterparties(uid));
    }

    /** Your accounts first, then the standing outside accounts, then any other names saved on entries. */
    static List<String> mergeParties(List<String> ownLabels, List<String> savedCounterparties) {
        LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
        for (List<String> group : List.of(ownLabels, STANDING_OUTSIDE_PARTIES, savedCounterparties)) {
            for (String name : group) {
                byKey.putIfAbsent(partyKey(name), name);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    @Transactional
    public RobinhoodCashIoEntryDto create(RobinhoodCashIoRequestDto body) {
        long uid = currentUser.requireUserId();
        ValidatedRequest req = validateRequest(body);
        Instant now = Instant.now();
        RobinhoodAccountCashIo row = new RobinhoodAccountCashIo();
        row.setOwnerUserId(uid);
        row.setAccountSuffix(req.suffix());
        row.setActivityDate(req.date());
        row.setDirection(req.direction());
        row.setAmount(req.amount());
        row.setNote(req.note());
        row.setCounterparty(req.counterparty());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        RobinhoodCashIoEntryDto saved = toEntryDto(repository.save(row), accountLabelMap(uid));
        cashIoDailyService.rebuildAfterLedgerChange(uid, req.suffix(), req.date());
        return saved;
    }

    @Transactional
    public RobinhoodCashIoEntryDto update(long id, RobinhoodCashIoRequestDto body) {
        long uid = currentUser.requireUserId();
        RobinhoodAccountCashIo row = requireOwned(id, uid);
        ValidatedRequest req = validateRequest(body);
        String previousSuffix = row.getAccountSuffix();
        LocalDate previousDate = row.getActivityDate();
        row.setAccountSuffix(req.suffix());
        row.setActivityDate(req.date());
        row.setDirection(req.direction());
        row.setAmount(req.amount());
        row.setNote(req.note());
        row.setCounterparty(req.counterparty());
        row.setUpdatedAt(Instant.now());
        RobinhoodCashIoEntryDto saved = toEntryDto(repository.save(row), accountLabelMap(uid));
        cashIoDailyService.rebuildAfterLedgerChange(uid, req.suffix(), req.date());
        if (!Objects.equals(previousSuffix, req.suffix()) || previousDate.getYear() != req.date().getYear()) {
            cashIoDailyService.rebuildAfterLedgerChange(uid, previousSuffix, previousDate);
        }
        return saved;
    }

    @Transactional
    public void delete(long id) {
        long uid = currentUser.requireUserId();
        RobinhoodAccountCashIo row = requireOwned(id, uid);
        String suffix = row.getAccountSuffix();
        LocalDate date = row.getActivityDate();
        repository.delete(row);
        cashIoDailyService.rebuildAfterLedgerChange(uid, suffix, date);
    }

    private RobinhoodAccountCashIo requireOwned(long id, long uid) {
        return repository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cash I/O entry not found"));
    }

    private List<RobinhoodAccountCashIo> loadRows(long uid, String suffix, LocalDate from, LocalDate to) {
        if (suffix != null) {
            return repository.findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                    uid, suffix, from, to);
        }
        return repository.findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(uid, from, to);
    }

    private ValidatedRequest validateRequest(RobinhoodCashIoRequestDto body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String suffix = normalizeRequiredSuffix(body.accountSuffix());
        if (body.activityDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activityDate is required");
        }
        String direction = normalizeDirection(body.direction());
        BigDecimal amount = body.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
        }
        String note = body.note() == null ? null : body.note().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        String counterparty = normalizeParty(body.counterparty());
        if (counterparty != null && counterparty.length() > MAX_PARTY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "counterparty must be at most " + MAX_PARTY_LENGTH + " characters");
        }
        return new ValidatedRequest(suffix, body.activityDate(), direction, scale(amount), note, counterparty);
    }

    /**
     * Keeps rows whose origin and receiving account match the given names. A blank filter matches everything.
     * For an output the origin is your own account and the receiver is the counterparty; for an input it is the
     * other way around. A side that was never recorded matches no named filter.
     */
    static List<RobinhoodAccountCashIo> filterByParties(
            List<RobinhoodAccountCashIo> rows, Map<String, String> accountLabels, String origin, String receiver) {
        String wantOrigin = partyKey(origin);
        String wantReceiver = partyKey(receiver);
        if (wantOrigin == null && wantReceiver == null) {
            return rows;
        }
        return rows.stream()
                .filter(r -> wantOrigin == null || wantOrigin.equals(partyKey(originOf(r, accountLabels))))
                .filter(r -> wantReceiver == null || wantReceiver.equals(partyKey(receiverOf(r, accountLabels))))
                .toList();
    }

    static String originOf(RobinhoodAccountCashIo row, Map<String, String> accountLabels) {
        return DIR_OUT.equals(row.getDirection()) ? ownLabel(row, accountLabels) : normalizeParty(row.getCounterparty());
    }

    static String receiverOf(RobinhoodAccountCashIo row, Map<String, String> accountLabels) {
        return DIR_OUT.equals(row.getDirection()) ? normalizeParty(row.getCounterparty()) : ownLabel(row, accountLabels);
    }

    private static String ownLabel(RobinhoodAccountCashIo row, Map<String, String> accountLabels) {
        return accountLabels.getOrDefault(row.getAccountSuffix(), "Account ••••" + row.getAccountSuffix());
    }

    private static String normalizeParty(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    /** Case-insensitive comparison key; null for blank. */
    private static String partyKey(String raw) {
        String s = normalizeParty(raw);
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static String normalizeDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction is required (IN or OUT)");
        }
        String d = raw.trim().toUpperCase(Locale.ROOT);
        if ("INPUT".equals(d) || "DEPOSIT".equals(d)) {
            d = DIR_IN;
        } else if ("OUTPUT".equals(d) || "WITHDRAWAL".equals(d) || "WITHDRAW".equals(d)) {
            d = DIR_OUT;
        }
        if (!DIR_IN.equals(d) && !DIR_OUT.equals(d)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction must be IN or OUT");
        }
        return d;
    }

    static String normalizeRequiredSuffix(String raw) {
        String s = normalizeOptionalSuffix(raw);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountSuffix is required");
        }
        if (!s.matches("\\d{3,8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "accountSuffix must be 3–8 digits (account last digits)");
        }
        return s;
    }

    private static String normalizeOptionalSuffix(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().replaceAll("^•+|\\.+", "");
        if (s.startsWith("••••")) {
            s = s.substring(4);
        }
        s = s.replaceAll("[^0-9]", "");
        return s.isEmpty() ? null : s;
    }

    Map<String, String> accountLabelMap(long uid) {
        Map<String, String> map = new LinkedHashMap<>();
        for (RobinhoodCashIoAccountDto a : listAccountsFor(uid)) {
            map.put(a.suffix(), a.label());
        }
        return map;
    }

    private List<RobinhoodCashIoAccountDto> listAccountsFor(long uid) {
        RobinhoodRhOwnedAccountsDto owned = accountTrackerConfigService.resolveOwnedAccounts(uid);
        LinkedHashSet<String> suffixes = new LinkedHashSet<>();
        if (owned.individualSuffix() != null) {
            suffixes.add(owned.individualSuffix());
        }
        if (owned.agenticSuffix() != null) {
            suffixes.add(owned.agenticSuffix());
        }
        if (owned.managedSuffix() != null) {
            suffixes.add(owned.managedSuffix());
        }
        suffixes.addAll(owned.trackedSuffixes());
        suffixes.addAll(owned.ownedSuffixes());
        suffixes.removeIf(s -> s == null || s.isBlank() || "0000".equals(s.trim()));
        List<RobinhoodCashIoAccountDto> out = new ArrayList<>();
        for (String suffix : suffixes) {
            out.add(toAccountDto(suffix, owned));
        }
        return out;
    }

    private static RobinhoodCashIoAccountDto toAccountDto(String suffix, RobinhoodRhOwnedAccountsDto owned) {
        String role;
        String labelPrefix;
        if (Objects.equals(suffix, owned.individualSuffix())) {
            role = "individual";
            labelPrefix = "Individual";
        } else if (Objects.equals(suffix, owned.agenticSuffix())) {
            role = "agentic";
            labelPrefix = "Agentic";
        } else if (Objects.equals(suffix, owned.managedSuffix())) {
            role = "managed";
            labelPrefix = "Managed";
        } else {
            role = "other";
            labelPrefix = "Account";
        }
        return new RobinhoodCashIoAccountDto(
                suffix, RobinhoodRhDailyTrackerAccountPolicy.named(suffix, labelPrefix), role);
    }

    private RobinhoodCashIoEntryDto toEntryDto(RobinhoodAccountCashIo row, Map<String, String> labels) {
        String label = labels.getOrDefault(
                row.getAccountSuffix(), RobinhoodRhDailyTrackerAccountPolicy.named(row.getAccountSuffix(), "Account"));
        return new RobinhoodCashIoEntryDto(
                row.getId(),
                row.getAccountSuffix(),
                label,
                row.getActivityDate(),
                row.getDirection(),
                scale(row.getAmount()),
                row.getNote(),
                normalizeParty(row.getCounterparty()),
                originOf(row, labels),
                receiverOf(row, labels),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static Totals totals(List<RobinhoodAccountCashIo> rows) {
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        for (RobinhoodAccountCashIo row : rows) {
            if (DIR_IN.equals(row.getDirection())) {
                in = in.add(row.getAmount());
            } else {
                out = out.add(row.getAmount());
            }
        }
        return new Totals(scale(in), scale(out), scale(in.subtract(out)));
    }

    private static DateWindow resolveWindow(int year, Integer month) {
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year out of range");
        }
        if (month == null) {
            return new DateWindow(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be 1–12");
        }
        YearMonth ym = YearMonth.of(year, month);
        return new DateWindow(ym.atDay(1), ym.atEndOfMonth());
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private record DateWindow(LocalDate from, LocalDate to) {}

    private record Totals(BigDecimal in, BigDecimal out, BigDecimal net) {}

    private record ValidatedRequest(
            String suffix,
            LocalDate date,
            String direction,
            BigDecimal amount,
            String note,
            String counterparty) {}

    private static final class MutableDay {
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        int count = 0;
    }
}
