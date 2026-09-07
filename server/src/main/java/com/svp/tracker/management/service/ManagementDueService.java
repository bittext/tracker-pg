package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.BankingTransaction;
import com.svp.tracker.finance.repository.BankingTransactionRepository;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementDueItem;
import com.svp.tracker.management.domain.ManagementDueOccurrence;
import com.svp.tracker.management.domain.ManagementDueSide;
import com.svp.tracker.management.dto.ManagementDueItemWriteRequest;
import com.svp.tracker.management.dto.ManagementDueMonthDto;
import com.svp.tracker.management.dto.ManagementDueOccurrenceDto;
import com.svp.tracker.management.dto.ManagementDueSettleRequest;
import com.svp.tracker.management.dto.ManagementDueSuggestionDto;
import com.svp.tracker.management.repository.ManagementDueItemRepository;
import com.svp.tracker.management.repository.ManagementDueOccurrenceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementDueService {

    private static final ZoneId OWNER_ZONE = ZoneId.of("America/Chicago");
    private static final int HISTORY_MONTHS = 24;
    private static final int ESTIMATE_SAMPLE = 8;
    private static final int SUGGESTION_LIMIT = 8;

    private final ManagementDueItemRepository itemRepository;
    private final ManagementDueOccurrenceRepository occurrenceRepository;
    private final BankingTransactionRepository bankingTransactionRepository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public ManagementDueMonthDto month(int year, int month) {
        YearMonth ym = requireYearMonth(year, month);
        long owner = currentUser.requireUserId();
        List<ManagementDueItem> items = itemRepository.findByOwnerUserIdAndActiveTrueOrderByCounterpartyAscIdAsc(owner);
        List<ManagementDueOccurrence> yearOcc = occurrenceRepository.findByOwnerAndYearWithItem(owner, year);
        History history = loadHistory(owner);

        Map<Long, ManagementDueOccurrence> occByItem = new HashMap<>();
        for (ManagementDueOccurrence occ : yearOcc) {
            if (occ.getMonth() == month && occ.getItem() != null && occ.getItem().getId() != null) {
                occByItem.put(occ.getItem().getId(), occ);
            }
        }

        Map<LocalDate, List<ManagementDueOccurrenceDto>> byDay = new LinkedHashMap<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            byDay.put(ym.atDay(d), new ArrayList<>());
        }

        for (ManagementDueItem item : items) {
            if (!ManagementDueCalendarSupport.appearsInMonth(
                    item.isRecurring(), item.getStartsOn(), item.getOneOffDate(), ym)) {
                continue;
            }
            LocalDate date = item.isRecurring()
                    ? ManagementDueCalendarSupport.occurrenceDate(year, month, item.getDayOfMonth())
                    : item.getOneOffDate();
            if (date == null || !YearMonth.from(date).equals(ym)) {
                continue;
            }
            ManagementDueOccurrence occ = occByItem.get(item.getId());
            byDay.computeIfAbsent(date, ignored -> new ArrayList<>()).add(toOccurrenceDto(item, date, occ, history));
        }

        List<ManagementDueMonthDto.Day> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ManagementDueOccurrenceDto>> entry : byDay.entrySet()) {
            List<ManagementDueOccurrenceDto> dayItems = entry.getValue();
            dayItems.sort(Comparator.comparing(ManagementDueOccurrenceDto::side)
                    .thenComparing(ManagementDueOccurrenceDto::counterparty, String.CASE_INSENSITIVE_ORDER));
            int payableCount = 0;
            int receivableCount = 0;
            BigDecimal payableTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal receivableTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            for (ManagementDueOccurrenceDto row : dayItems) {
                BigDecimal amt = row.displayAmount() == null ? BigDecimal.ZERO : row.displayAmount();
                if ("PAYABLE".equals(row.side())) {
                    payableCount += 1;
                    payableTotal = payableTotal.add(amt);
                } else {
                    receivableCount += 1;
                    receivableTotal = receivableTotal.add(amt);
                }
            }
            days.add(new ManagementDueMonthDto.Day(
                    entry.getKey(), payableCount, receivableCount, payableTotal, receivableTotal, List.copyOf(dayItems)));
        }

        Totals monthTotals = totalsFor(yearOcc, month);
        Totals yearTotals = totalsFor(yearOcc, null);
        return new ManagementDueMonthDto(
                year,
                month,
                monthTotals.paid,
                monthTotals.received,
                monthTotals.received.subtract(monthTotals.paid),
                yearTotals.paid,
                yearTotals.received,
                yearTotals.received.subtract(yearTotals.paid),
                days,
                suggestions(items, history));
    }

    @Transactional
    public ManagementDueMonthDto create(ManagementDueItemWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        ManagementDueItem item = new ManagementDueItem();
        item.setOwnerUserId(owner);
        item.setStartsOn(null);
        applyWrite(item, req, todayInOwnerZone().withDayOfMonth(1), true);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        itemRepository.save(item);
        YearMonth focus = focusMonth(req);
        return month(focus.getYear(), focus.getMonthValue());
    }

    @Transactional
    public ManagementDueMonthDto update(long id, ManagementDueItemWriteRequest req) {
        long owner = currentUser.requireUserId();
        ManagementDueItem item = itemRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Due item not found: " + id));
        applyWrite(item, req, item.getStartsOn(), false);
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        YearMonth focus = focusMonth(req);
        return month(focus.getYear(), focus.getMonthValue());
    }

    @Transactional
    public ManagementDueMonthDto delete(long id, int year, int month) {
        requireYearMonth(year, month);
        long owner = currentUser.requireUserId();
        ManagementDueItem item = itemRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Due item not found: " + id));
        itemRepository.delete(item);
        return month(year, month);
    }

    @Transactional
    public ManagementDueMonthDto settle(long id, ManagementDueSettleRequest req) {
        requireYearMonth(req.year(), req.month());
        long owner = currentUser.requireUserId();
        ManagementDueItem item = itemRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Due item not found: " + id));
        ManagementDueOccurrence occ = occurrenceRepository
                .findByItem_IdAndYearAndMonth(id, req.year(), req.month())
                .orElseGet(() -> {
                    ManagementDueOccurrence created = new ManagementDueOccurrence();
                    created.setItem(item);
                    created.setOwnerUserId(owner);
                    created.setYear(req.year());
                    created.setMonth(req.month());
                    return created;
                });
        occ.setSettled(req.settled());
        if (req.settled()) {
            BigDecimal amount = req.settledAmount();
            if (amount == null) {
                History history = loadHistory(owner);
                LocalDate date = item.isRecurring()
                        ? ManagementDueCalendarSupport.occurrenceDate(req.year(), req.month(), item.getDayOfMonth())
                        : item.getOneOffDate();
                amount = displayAmount(item, history);
                if (amount == null && date != null) {
                    amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
            }
            occ.setSettledAmount(scale(amount));
            occ.setSettledAt(Instant.now());
        } else {
            occ.setSettledAmount(null);
            occ.setSettledAt(null);
        }
        occurrenceRepository.save(occ);
        return month(req.year(), req.month());
    }

    private void applyWrite(
            ManagementDueItem item, ManagementDueItemWriteRequest req, LocalDate fallbackStart, boolean setStart) {
        ManagementDueSide side;
        try {
            side = ManagementDueSide.valueOf(req.side().trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "side must be PAYABLE or RECEIVABLE");
        }
        boolean recurring = req.recurring();
        if (recurring) {
            if (req.dayOfMonth() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayOfMonth is required for recurring items");
            }
            item.setDayOfMonth(req.dayOfMonth());
            item.setOneOffDate(null);
        } else {
            if (req.oneOffDate() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oneOffDate is required for one-time items");
            }
            item.setDayOfMonth(null);
            item.setOneOffDate(req.oneOffDate());
        }
        item.setSide(side);
        item.setCounterparty(req.counterparty().trim());
        item.setRecurring(recurring);
        item.setAmountOverride(scale(req.amountOverride()));
        item.setNotes(req.notes() == null ? "" : req.notes().trim());
        item.setActive(true);
        if (setStart) {
            if (req.startYear() != null && req.startMonth() != null) {
                item.setStartsOn(YearMonth.of(req.startYear(), req.startMonth()).atDay(1));
            } else if (item.getStartsOn() == null) {
                item.setStartsOn(fallbackStart != null ? fallbackStart : todayInOwnerZone().withDayOfMonth(1));
            }
        }
    }

    private YearMonth focusMonth(ManagementDueItemWriteRequest req) {
        if (req.startYear() != null && req.startMonth() != null) {
            return YearMonth.of(req.startYear(), req.startMonth());
        }
        if (!req.recurring() && req.oneOffDate() != null) {
            return YearMonth.from(req.oneOffDate());
        }
        return YearMonth.from(todayInOwnerZone());
    }

    private LocalDate todayInOwnerZone() {
        return LocalDate.now(OWNER_ZONE);
    }

    private YearMonth requireYearMonth(int year, int month) {
        if (year < 1970 || year > 9999 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year or month");
        }
        return YearMonth.of(year, month);
    }

    private ManagementDueOccurrenceDto toOccurrenceDto(
            ManagementDueItem item, LocalDate date, ManagementDueOccurrence occ, History history) {
        BigDecimal estimated = estimate(item, history);
        BigDecimal override = item.getAmountOverride();
        String source;
        BigDecimal display;
        if (occ != null && occ.isSettled() && occ.getSettledAmount() != null) {
            display = occ.getSettledAmount();
            source = "settled";
        } else if (override != null) {
            display = override;
            source = "override";
        } else if (estimated != null) {
            display = estimated;
            source = "history";
        } else {
            display = null;
            source = "none";
        }
        return new ManagementDueOccurrenceDto(
                item.getId(),
                occ == null ? null : occ.getId(),
                item.getSide().name(),
                item.getCounterparty(),
                item.isRecurring(),
                item.getDayOfMonth(),
                item.getOneOffDate(),
                date,
                override,
                estimated,
                display,
                source,
                item.getNotes(),
                occ != null && occ.isSettled(),
                occ == null ? null : occ.getSettledAmount());
    }

    private BigDecimal displayAmount(ManagementDueItem item, History history) {
        if (item.getAmountOverride() != null) {
            return item.getAmountOverride();
        }
        return estimate(item, history);
    }

    private BigDecimal estimate(ManagementDueItem item, History history) {
        List<BigDecimal> samples = new ArrayList<>();
        for (HistoryRow row : history.rows()) {
            if (history.internalIds().contains(row.id())) {
                continue;
            }
            if (!ManagementDueCalendarSupport.payeeMatches(item.getCounterparty(), row.description())) {
                continue;
            }
            if (item.getSide() == ManagementDueSide.PAYABLE && row.amount().signum() >= 0) {
                continue;
            }
            if (item.getSide() == ManagementDueSide.RECEIVABLE && row.amount().signum() <= 0) {
                continue;
            }
            samples.add(row.amount().abs());
            if (samples.size() >= ESTIMATE_SAMPLE) {
                break;
            }
        }
        return ManagementDueCalendarSupport.median(samples);
    }

    private List<ManagementDueSuggestionDto> suggestions(List<ManagementDueItem> items, History history) {
        Map<String, SuggestionAcc> acc = new LinkedHashMap<>();
        for (HistoryRow row : history.rows()) {
            if (history.internalIds().contains(row.id()) || row.amount().signum() == 0) {
                continue;
            }
            String key = ManagementDueCalendarSupport.normalizePayee(row.description());
            if (key.length() < 4) {
                continue;
            }
            SuggestionAcc bucket = acc.computeIfAbsent(key, ignored -> new SuggestionAcc());
            bucket.counterparty = row.description().trim();
            bucket.side = row.amount().signum() < 0 ? ManagementDueSide.PAYABLE : ManagementDueSide.RECEIVABLE;
            bucket.amounts.add(row.amount().abs());
            bucket.days.add(row.date().getDayOfMonth());
        }
        List<ManagementDueSuggestionDto> out = new ArrayList<>();
        for (SuggestionAcc bucket : acc.values()) {
            if (bucket.amounts.size() < 2) {
                continue;
            }
            if (alreadyTracked(items, bucket.side, bucket.counterparty)) {
                continue;
            }
            out.add(new ManagementDueSuggestionDto(
                    bucket.side.name(),
                    shorten(bucket.counterparty),
                    ManagementDueCalendarSupport.medianDay(bucket.days),
                    ManagementDueCalendarSupport.median(bucket.amounts),
                    bucket.amounts.size()));
        }
        out.sort(Comparator.comparingInt(ManagementDueSuggestionDto::sampleCount).reversed());
        if (out.size() > SUGGESTION_LIMIT) {
            return List.copyOf(out.subList(0, SUGGESTION_LIMIT));
        }
        return List.copyOf(out);
    }

    private boolean alreadyTracked(List<ManagementDueItem> items, ManagementDueSide side, String counterparty) {
        for (ManagementDueItem item : items) {
            if (item.getSide() == side && ManagementDueCalendarSupport.payeeMatches(item.getCounterparty(), counterparty)) {
                return true;
            }
        }
        return false;
    }

    private History loadHistory(long owner) {
        LocalDate from = todayInOwnerZone().minusMonths(HISTORY_MONTHS);
        List<BankingTransaction> txns = bankingTransactionRepository.listSince(owner, from);
        List<ManagementDueTransferClassifier.TxnView> views = new ArrayList<>();
        List<HistoryRow> rows = new ArrayList<>();
        for (BankingTransaction txn : txns) {
            long instId = txn.getInstitution() == null || txn.getInstitution().getId() == null
                    ? 0L
                    : txn.getInstitution().getId();
            views.add(new ManagementDueTransferClassifier.TxnView(
                    txn.getId(), instId, txn.getTxnDate(), txn.getAmount(), txn.getDescription()));
            rows.add(new HistoryRow(txn.getId(), txn.getTxnDate(), txn.getAmount(), txn.getDescription()));
        }
        return new History(rows, ManagementDueTransferClassifier.internalTransferIds(views));
    }

    private Totals totalsFor(List<ManagementDueOccurrence> yearOcc, Integer monthOrNull) {
        BigDecimal paid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal received = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (ManagementDueOccurrence occ : yearOcc) {
            if (!occ.isSettled()) {
                continue;
            }
            if (monthOrNull != null && occ.getMonth() != monthOrNull) {
                continue;
            }
            ManagementDueItem item = occ.getItem();
            if (item == null || item.getSide() == null) {
                continue;
            }
            BigDecimal amt = occ.getSettledAmount() == null
                    ? BigDecimal.ZERO
                    : occ.getSettledAmount();
            if (item.getSide() == ManagementDueSide.PAYABLE) {
                paid = paid.add(amt);
            } else {
                received = received.add(amt);
            }
        }
        return new Totals(paid, received);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String shorten(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.length() > 48 ? trimmed.substring(0, 48).trim() : trimmed;
    }

    private record History(List<HistoryRow> rows, Set<Long> internalIds) {}

    private record HistoryRow(long id, LocalDate date, BigDecimal amount, String description) {}

    private record Totals(BigDecimal paid, BigDecimal received) {}

    private static final class SuggestionAcc {
        String counterparty = "";
        ManagementDueSide side = ManagementDueSide.PAYABLE;
        final List<BigDecimal> amounts = new ArrayList<>();
        final List<Integer> days = new ArrayList<>();
    }
}
