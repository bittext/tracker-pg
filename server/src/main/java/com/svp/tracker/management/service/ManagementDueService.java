package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.BankingTransaction;
import com.svp.tracker.finance.repository.BankingTransactionRepository;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementDesk;
import com.svp.tracker.management.domain.ManagementDueCategory;
import com.svp.tracker.management.domain.ManagementDueItem;
import com.svp.tracker.management.domain.ManagementDueOccurrence;
import com.svp.tracker.management.domain.ManagementDueSide;
import com.svp.tracker.management.dto.ManagementDueItemWriteRequest;
import com.svp.tracker.management.dto.ManagementDueMonthDto;
import com.svp.tracker.management.dto.ManagementDueOccurrenceDto;
import com.svp.tracker.management.dto.ManagementDueReportDto;
import com.svp.tracker.management.dto.ManagementDueSettleRequest;
import com.svp.tracker.management.dto.ManagementDueSuggestionDto;
import com.svp.tracker.management.repository.ManagementDueCategoryRepository;
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

    private final ManagementDueItemRepository itemRepository;
    private final ManagementDueOccurrenceRepository occurrenceRepository;
    private final ManagementDueCategoryRepository categoryRepository;
    private final BankingTransactionRepository bankingTransactionRepository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public ManagementDueMonthDto month(int year, int month) {
        YearMonth ym = requireYearMonth(year, month);
        long owner = currentUser.requireUserId();
        List<ManagementDueItem> items = activeItems(owner);
        List<ManagementDueOccurrence> yearOcc = occurrenceRepository.findByOwnerAndYearWithItem(owner, year);
        History history = loadHistory(owner);
        Map<Long, ManagementDueCategory> categories = categoriesFor(owner);

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
                    item.isRecurring(),
                    item.getStartsOn(),
                    item.getOneOffDate(),
                    ym,
                    YearMonth.from(todayInOwnerZone()))) {
                continue;
            }
            LocalDate date = occurrenceOn(item, year, month);
            if (date == null || !YearMonth.from(date).equals(ym)) {
                continue;
            }
            ManagementDueOccurrence occ = occByItem.get(item.getId());
            if (skipped(occ)) {
                continue;
            }
            byDay.computeIfAbsent(date, ignored -> new ArrayList<>())
                    .add(toOccurrenceDto(item, date, occ, history, categories));
        }

        List<ManagementDueMonthDto.Day> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ManagementDueOccurrenceDto>> entry : byDay.entrySet()) {
            List<ManagementDueOccurrenceDto> dayItems = entry.getValue();
            dayItems.sort(Comparator.comparingInt(
                            (ManagementDueOccurrenceDto row) ->
                                    row.categorySort() == null ? Integer.MIN_VALUE : row.categorySort())
                    .reversed()
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

        Map<String, ManagementDueOccurrence> occByKey = indexOccurrences(yearOcc);
        Totals monthTotals = totalsFor(items, occByKey, year, month);
        Totals yearTotals = totalsFor(items, occByKey, year, null);
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
                suggestions(items, history, ym));
    }

    @Transactional(readOnly = true)
    public ManagementDueReportDto reports(int year, int month) {
        requireYearMonth(year, month);
        long owner = currentUser.requireUserId();
        List<ManagementDueItem> items = activeItems(owner);
        List<ManagementDueOccurrence> allOcc = occurrenceRepository.findByOwnerWithItem(owner);
        History history = loadHistory(owner);
        Map<Long, ManagementDueCategory> categories = categoriesFor(owner);
        YearMonth today = YearMonth.from(todayInOwnerZone());

        Map<String, ManagementDueOccurrence> occByItemYm = new HashMap<>();
        for (ManagementDueOccurrence occ : allOcc) {
            if (occ.getItem() != null && occ.getItem().getId() != null) {
                occByItemYm.put(occKey(occ.getItem().getId(), occ.getYear(), occ.getMonth()), occ);
            }
        }

        List<ManagementDueReportDto.Row> rows = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            for (ManagementDueItem item : items) {
                if (!ManagementDueCalendarSupport.appearsInMonth(
                        item.isRecurring(),
                        item.getStartsOn(),
                        item.getOneOffDate(),
                        ym,
                        today)) {
                    continue;
                }
                LocalDate date = occurrenceOn(item, year, m);
                if (date == null || !YearMonth.from(date).equals(ym)) {
                    continue;
                }
                ManagementDueOccurrence occ = occByItemYm.get(occKey(item.getId(), year, m));
                if (skipped(occ)) {
                    continue;
                }
                rows.add(toReportRow(toOccurrenceDto(item, date, occ, history, categories), year, m));
            }
        }
        return ManagementDueReportSupport.build(year, month, rows);
    }

    @Transactional
    public ManagementDueMonthDto clearLaterDates(int year, int month) {
        requireYearMonth(year, month);
        long owner = currentUser.requireUserId();
        LocalDate today = todayInOwnerZone();
        List<ManagementDueOccurrence> yearOcc = occurrenceRepository.findByOwnerAndYearWithItem(owner, year);
        Map<Long, ManagementDueOccurrence> occByItem = new HashMap<>();
        for (ManagementDueOccurrence occ : yearOcc) {
            if (occ.getMonth() == month && occ.getItem() != null && occ.getItem().getId() != null) {
                occByItem.put(occ.getItem().getId(), occ);
            }
        }
        for (ManagementDueItem item : activeItems(owner)) {
            if (item.isRecurring()) {
                continue;
            }
            LocalDate date = item.getOneOffDate();
            if (date == null || !date.isAfter(today)) {
                continue;
            }
            ManagementDueOccurrence occ = occByItem.get(item.getId());
            if (occ != null && occ.isSettled()) {
                continue;
            }
            itemRepository.delete(item);
        }
        return month(year, month);
    }

    @Transactional
    public ManagementDueMonthDto create(ManagementDueItemWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        ManagementDueItem item = new ManagementDueItem();
        item.setOwnerUserId(owner);
        item.setDesk(ManagementDesk.LIFE);
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
        if (item.getAmountOverride() != null) {
            occurrenceRepository
                    .findByItem_IdAndYearAndMonth(item.getId(), focus.getYear(), focus.getMonthValue())
                    .filter(ManagementDueOccurrence::isSettled)
                    .ifPresent(occ -> {
                        occ.setSettledAmount(item.getAmountOverride());
                        occ.setSettledAt(Instant.now());
                        occurrenceRepository.save(occ);
                    });
        }
        return month(focus.getYear(), focus.getMonthValue());
    }

    @Transactional
    public ManagementDueMonthDto delete(long id, int year, int month) {
        requireYearMonth(year, month);
        long owner = currentUser.requireUserId();
        ManagementDueItem item = itemRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Due item not found: " + id));
        YearMonth ym = YearMonth.of(year, month);
        if (!ManagementDueCalendarSupport.appearsInMonth(
                item.isRecurring(),
                item.getStartsOn(),
                item.getOneOffDate(),
                ym,
                YearMonth.from(todayInOwnerZone()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That due is not on this month");
        }
        if (!item.isRecurring()) {
            itemRepository.delete(item);
            return month(year, month);
        }
        ManagementDueOccurrence occ = occurrenceRepository
                .findByItem_IdAndYearAndMonth(id, year, month)
                .orElseGet(() -> {
                    ManagementDueOccurrence created = new ManagementDueOccurrence();
                    created.setItem(item);
                    created.setOwnerUserId(owner);
                    created.setYear(year);
                    created.setMonth(month);
                    return created;
                });
        occ.setSkipped(true);
        occ.setSettled(false);
        occ.setSettledAmount(null);
        occ.setSettledAt(null);
        occurrenceRepository.save(occ);
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
        item.setCategoryId(resolveCategoryId(item.getOwnerUserId(), req.categoryId()));
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

    private Long resolveCategoryId(Long ownerUserId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository
                .findByIdAndOwnerUserId(categoryId, ownerUserId)
                .map(ManagementDueCategory::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown subcategory"));
    }

    private Map<Long, ManagementDueCategory> categoriesFor(long owner) {
        Map<Long, ManagementDueCategory> map = new HashMap<>();
        for (ManagementDueCategory category : categoryRepository.findByOwnerUserIdOrderBySortOrderDescNameAsc(owner)) {
            if (category.getId() != null) {
                map.put(category.getId(), category);
            }
        }
        return map;
    }

    private ManagementDueOccurrenceDto toOccurrenceDto(
            ManagementDueItem item,
            LocalDate date,
            ManagementDueOccurrence occ,
            History history,
            Map<Long, ManagementDueCategory> categories) {
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
        ManagementDueCategory category =
                item.getCategoryId() == null ? null : categories.get(item.getCategoryId());
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
                occ == null ? null : occ.getSettledAmount(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getSortOrder());
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

    private List<ManagementDueSuggestionDto> suggestions(
            List<ManagementDueItem> items, History history, YearMonth focus) {
        List<ManagementDueSuggestionSupport.Tracked> tracked = new ArrayList<>();
        for (ManagementDueItem item : items) {
            tracked.add(new ManagementDueSuggestionSupport.Tracked(item.getSide(), item.getCounterparty()));
        }
        List<ManagementDueSuggestionSupport.Txn> rows = new ArrayList<>();
        for (HistoryRow row : history.rows()) {
            rows.add(new ManagementDueSuggestionSupport.Txn(row.id(), row.date(), row.amount(), row.description()));
        }
        return ManagementDueSuggestionSupport.build(rows, history.internalIds(), tracked, focus);
    }

    private List<ManagementDueItem> activeItems(long owner) {
        return itemRepository.findByOwnerUserIdAndDeskAndActiveTrueOrderByCounterpartyAscIdAsc(
                owner, ManagementDesk.LIFE);
    }

    private static ManagementDueReportDto.Row toReportRow(ManagementDueOccurrenceDto dto, int year, int month) {
        return new ManagementDueReportDto.Row(
                dto.itemId(),
                dto.occurrenceId(),
                year,
                month,
                dto.occurrenceDate(),
                dto.side(),
                dto.counterparty(),
                dto.recurring(),
                dto.notes() == null ? "" : dto.notes(),
                dto.settled(),
                dto.displayAmount(),
                dto.amountSource(),
                dto.category() == null ? "" : dto.category());
    }

    private static String occKey(long itemId, int year, int month) {
        return itemId + ":" + year + ":" + month;
    }

    private static LocalDate occurrenceOn(ManagementDueItem item, int year, int month) {
        if (item.isRecurring()) {
            if (item.getDayOfMonth() == null) {
                return null;
            }
            return ManagementDueCalendarSupport.occurrenceDate(year, month, item.getDayOfMonth());
        }
        return item.getOneOffDate();
    }

    private History loadHistory(long owner) {
        LocalDate from = todayInOwnerZone().minusMonths(HISTORY_MONTHS);
        List<BankingTransaction> txns = bankingTransactionRepository.listSince(owner, from);
        List<ManagementDueTransferClassifier.TxnView> views = new ArrayList<>();
        List<HistoryRow> rows = new ArrayList<>();
        for (BankingTransaction txn : txns) {
            if (!isBankingAccountTxn(txn)) {
                continue;
            }
            long instId = txn.getInstitution() == null || txn.getInstitution().getId() == null
                    ? 0L
                    : txn.getInstitution().getId();
            views.add(new ManagementDueTransferClassifier.TxnView(
                    txn.getId(), instId, txn.getTxnDate(), txn.getAmount(), txn.getDescription()));
            rows.add(new HistoryRow(txn.getId(), txn.getTxnDate(), txn.getAmount(), txn.getDescription()));
        }
        return new History(rows, ManagementDueTransferClassifier.internalTransferIds(views));
    }

    private static boolean skipped(ManagementDueOccurrence occ) {
        return occ != null && occ.isSkipped();
    }

    private static boolean isBankingAccountTxn(BankingTransaction txn) {
        if (txn.getInstitution() == null || txn.getInstitution().getInstitutionType() == null) {
            return false;
        }
        return ManagementDueBankAccountSupport.isBankingAccountType(
                txn.getInstitution().getInstitutionType().getName());
    }

    private Map<String, ManagementDueOccurrence> indexOccurrences(List<ManagementDueOccurrence> occurrences) {
        Map<String, ManagementDueOccurrence> occByKey = new HashMap<>();
        for (ManagementDueOccurrence occ : occurrences) {
            if (occ.getItem() != null && occ.getItem().getId() != null) {
                occByKey.put(occKey(occ.getItem().getId(), occ.getYear(), occ.getMonth()), occ);
            }
        }
        return occByKey;
    }

    /** Paid and received totals follow the rows the calendar shows for that month. */
    private Totals totalsFor(
            List<ManagementDueItem> items,
            Map<String, ManagementDueOccurrence> occByKey,
            int year,
            Integer monthOrNull) {
        BigDecimal paid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal received = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        YearMonth today = YearMonth.from(todayInOwnerZone());
        int from = monthOrNull == null ? 1 : monthOrNull;
        int to = monthOrNull == null ? 12 : monthOrNull;
        for (int m = from; m <= to; m++) {
            YearMonth ym = YearMonth.of(year, m);
            for (ManagementDueItem item : items) {
                if (item.getSide() == null) {
                    continue;
                }
                if (!ManagementDueCalendarSupport.appearsInMonth(
                        item.isRecurring(),
                        item.getStartsOn(),
                        item.getOneOffDate(),
                        ym,
                        today)) {
                    continue;
                }
                LocalDate date = occurrenceOn(item, year, m);
                if (date == null || !YearMonth.from(date).equals(ym)) {
                    continue;
                }
                ManagementDueOccurrence occ = occByKey.get(occKey(item.getId(), year, m));
                if (skipped(occ) || occ == null || !occ.isSettled()) {
                    continue;
                }
                BigDecimal amt = occ.getSettledAmount() == null ? BigDecimal.ZERO : occ.getSettledAmount();
                if (item.getSide() == ManagementDueSide.PAYABLE) {
                    paid = paid.add(amt);
                } else {
                    received = received.add(amt);
                }
            }
        }
        return new Totals(paid, received);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record History(List<HistoryRow> rows, Set<Long> internalIds) {}

    private record HistoryRow(long id, LocalDate date, BigDecimal amount, String description) {}

    private record Totals(BigDecimal paid, BigDecimal received) {}
}
