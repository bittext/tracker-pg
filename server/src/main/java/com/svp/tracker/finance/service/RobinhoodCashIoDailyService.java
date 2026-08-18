package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodCashIoDaily;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import com.svp.tracker.finance.dto.RobinhoodCashIoDailyDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoDailyHistoryDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoLiveAccountDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoYtdDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoYtdEventDto;
import com.svp.tracker.finance.dto.RobinhoodRhOwnedAccountsDto;
import com.svp.tracker.finance.repository.RobinhoodCashIoDailyRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodCashIoDailyService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final TypeReference<List<RobinhoodCashIoLiveAccountDto>> LIVE_ACCOUNTS =
            new TypeReference<>() {};

    private final CurrentUserService currentUser;
    private final RobinhoodCashIoYtdService ytdService;
    private final RobinhoodCashIoDailyRepository dailyRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional
    public RobinhoodCashIoDailyHistoryDto history(int year, String accountSuffix) {
        long uid = currentUser.requireUserId();
        String suffix = RobinhoodCashIoYtdService.DEFAULT_SUFFIX;
        if (accountSuffix != null && !accountSuffix.isBlank()) {
            suffix = accountSuffix.replaceAll("\\D", "");
        }
        RobinhoodCashIoYtdDto ytd = rebuild(uid, suffix, year);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<RobinhoodCashIoDailyDto> days = dailyRepository
                .findByOwnerUserIdAndAccountSuffixAndAsOfDateBetweenOrderByAsOfDateDesc(uid, suffix, from, to)
                .stream()
                .map(this::toDto)
                .toList();
        return new RobinhoodCashIoDailyHistoryDto(suffix, ytd.accountLabel(), year, days);
    }

    @Transactional
    public void captureForOwner(long uid) {
        int year = LocalDate.now(CENTRAL).getYear();
        RobinhoodRhOwnedAccountsDto owned = accountTrackerConfigService.resolveOwnedAccounts(uid);
        List<String> suffixes = new ArrayList<>();
        if (owned.individualSuffix() != null && !owned.individualSuffix().isBlank()) {
            suffixes.add(owned.individualSuffix());
        }
        if (owned.agenticSuffix() != null && !owned.agenticSuffix().isBlank()) {
            suffixes.add(owned.agenticSuffix());
        }
        if (owned.managedSuffix() != null && !owned.managedSuffix().isBlank()) {
            suffixes.add(owned.managedSuffix());
        }
        if (suffixes.isEmpty()) {
            suffixes.add(RobinhoodCashIoYtdService.DEFAULT_SUFFIX);
        }
        for (String suffix : suffixes) {
            try {
                rebuild(uid, suffix, year);
            } catch (Exception e) {
                log.warn("Cash I/O daily capture failed for user {} suffix {}: {}", uid, suffix, e.getMessage());
            }
        }
    }

    @Transactional
    public void rebuildAfterLedgerChange(long uid, String suffix, LocalDate activityDate) {
        if (suffix == null || suffix.isBlank() || activityDate == null) {
            return;
        }
        try {
            rebuild(uid, suffix, activityDate.getYear());
            int thisYear = LocalDate.now(CENTRAL).getYear();
            if (activityDate.getYear() != thisYear) {
                rebuild(uid, suffix, thisYear);
            }
        } catch (Exception e) {
            log.warn(
                    "Cash I/O daily rebuild failed for user {} suffix {} year {}: {}",
                    uid,
                    suffix,
                    activityDate.getYear(),
                    e.getMessage());
        }
    }

    RobinhoodCashIoYtdDto rebuild(long uid, String suffix, int year) {
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year is out of range");
        }
        RobinhoodCashIoYtdDto ytd = ytdService.ytdFor(uid, year, suffix);
        LocalDate start = ytd.startDate() != null ? ytd.startDate() : LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate today = LocalDate.now(CENTRAL);
        LocalDate end = today.isBefore(yearEnd) ? today : yearEnd;
        if (end.isBefore(start)) {
            return ytd;
        }

        Map<LocalDate, DayAcc> byDay = new HashMap<>();
        for (RobinhoodCashIoYtdEventDto e : ytd.events()) {
            if (e.date() == null || "START".equals(e.kind())) {
                continue;
            }
            DayAcc acc = byDay.computeIfAbsent(e.date(), d -> new DayAcc());
            switch (e.kind()) {
                case "INPUT" -> acc.inputs = acc.inputs.add(e.amount());
                case "OUTPUT" -> acc.outputs = acc.outputs.add(e.amount());
                case "CREDIT" -> acc.credits = acc.credits.add(e.amount());
                case "DEBIT" -> acc.debits = acc.debits.add(e.amount());
                default -> {
                }
            }
            acc.adjustedNow = e.runningAdjusted();
        }

        LiveBook liveBook = liveBook(uid, start.minusDays(45), end, suffix);
        liveBook.primeBefore(start);
        Instant capturedAt = Instant.now();
        BigDecimal ytdIn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ytdOut = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ytdCr = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ytdDb = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjusted = scale(ytd.startingCash());

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayAcc acc = byDay.get(d);
            BigDecimal dayIn = acc != null ? scale(acc.inputs) : zero();
            BigDecimal dayOut = acc != null ? scale(acc.outputs) : zero();
            BigDecimal dayCr = acc != null ? scale(acc.credits) : zero();
            BigDecimal dayDb = acc != null ? scale(acc.debits) : zero();
            ytdIn = ytdIn.add(dayIn);
            ytdOut = ytdOut.add(dayOut);
            ytdCr = ytdCr.add(dayCr);
            ytdDb = ytdDb.add(dayDb);
            if (acc != null && acc.adjustedNow != null) {
                adjusted = scale(acc.adjustedNow);
            }
            List<RobinhoodCashIoLiveAccountDto> lives = liveBook.asOf(d);
            BigDecimal thisLive = lives.stream()
                    .filter(a -> suffix.equals(a.suffix()))
                    .map(RobinhoodCashIoLiveAccountDto::value)
                    .findFirst()
                    .orElse(null);
            upsert(
                    uid,
                    suffix,
                    d,
                    dayIn,
                    dayOut,
                    dayCr,
                    dayDb,
                    scale(ytdIn),
                    scale(ytdOut),
                    scale(ytdCr),
                    scale(ytdDb),
                    adjusted,
                    thisLive,
                    lives,
                    capturedAt);
        }
        return ytd;
    }

    private void upsert(
            long uid,
            String suffix,
            LocalDate date,
            BigDecimal dayIn,
            BigDecimal dayOut,
            BigDecimal dayCr,
            BigDecimal dayDb,
            BigDecimal ytdIn,
            BigDecimal ytdOut,
            BigDecimal ytdCr,
            BigDecimal ytdDb,
            BigDecimal adjusted,
            BigDecimal liveValue,
            List<RobinhoodCashIoLiveAccountDto> lives,
            Instant capturedAt) {
        RobinhoodCashIoDaily row = dailyRepository
                .findByOwnerUserIdAndAccountSuffixAndAsOfDate(uid, suffix, date)
                .orElseGet(RobinhoodCashIoDaily::new);
        row.setOwnerUserId(uid);
        row.setAccountSuffix(suffix);
        row.setAsOfDate(date);
        row.setDayInputs(dayIn);
        row.setDayOutputs(dayOut);
        row.setDayCredits(dayCr);
        row.setDayDebits(dayDb);
        row.setYtdInputs(ytdIn);
        row.setYtdOutputs(ytdOut);
        row.setYtdCredits(ytdCr);
        row.setYtdDebits(ytdDb);
        row.setAdjustedNow(adjusted);
        row.setLiveValue(liveValue);
        row.setLiveAccountsJson(writeLives(lives));
        row.setCapturedAt(capturedAt);
        dailyRepository.save(row);
    }

    private LiveBook liveBook(long uid, LocalDate from, LocalDate to, String trackSuffix) {
        RobinhoodRhOwnedAccountsDto owned = accountTrackerConfigService.resolveOwnedAccounts(uid);
        Map<String, String> labels = new LinkedHashMap<>();
        addLabel(labels, owned.individualSuffix(), "Individual");
        addLabel(labels, owned.agenticSuffix(), "Agentic");
        addLabel(labels, owned.managedSuffix(), "Managed");
        if (!labels.containsKey(trackSuffix)) {
            labels.put(trackSuffix, "Account");
        }
        TreeMap<LocalDate, Map<String, BigDecimal>> byDate = new TreeMap<>();
        for (RhScheduledTotalRow row : snapshotRepository.findScheduledTotalsBetween(uid, from, to)) {
            if (row.snapshotDate() == null || row.accountSuffix() == null || row.totalAccountValue() == null) {
                continue;
            }
            byDate.computeIfAbsent(row.snapshotDate(), d -> new HashMap<>())
                    .put(row.accountSuffix(), scale(row.totalAccountValue()));
        }
        return new LiveBook(labels, byDate);
    }

    private static void addLabel(Map<String, String> labels, String suffix, String role) {
        if (suffix == null || suffix.isBlank()) {
            return;
        }
        labels.put(suffix, role + " ••••" + suffix);
    }

    private RobinhoodCashIoDailyDto toDto(RobinhoodCashIoDaily row) {
        return new RobinhoodCashIoDailyDto(
                row.getAsOfDate(),
                scale(row.getDayInputs()),
                scale(row.getDayOutputs()),
                scale(row.getDayCredits()),
                scale(row.getDayDebits()),
                scale(row.getYtdInputs()),
                scale(row.getYtdOutputs()),
                scale(row.getYtdCredits()),
                scale(row.getYtdDebits()),
                scale(row.getAdjustedNow()),
                row.getLiveValue() != null ? scale(row.getLiveValue()) : null,
                readLives(row.getLiveAccountsJson()),
                row.getCapturedAt());
    }

    private List<RobinhoodCashIoLiveAccountDto> readLives(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<RobinhoodCashIoLiveAccountDto> list = objectMapper.readValue(json, LIVE_ACCOUNTS);
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeLives(List<RobinhoodCashIoLiveAccountDto> lives) {
        try {
            return objectMapper.writeValueAsString(lives != null ? lives : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }

    private static BigDecimal scale(BigDecimal v) {
        if (v == null) {
            return zero();
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class DayAcc {
        private BigDecimal inputs = zero();
        private BigDecimal outputs = zero();
        private BigDecimal credits = zero();
        private BigDecimal debits = zero();
        private BigDecimal adjustedNow;
    }

    private static final class LiveBook {
        private final Map<String, String> labels;
        private final TreeMap<LocalDate, Map<String, BigDecimal>> byDate;
        private final Map<String, BigDecimal> last = new HashMap<>();

        private LiveBook(Map<String, String> labels, TreeMap<LocalDate, Map<String, BigDecimal>> byDate) {
            this.labels = labels;
            this.byDate = byDate;
        }

        private void primeBefore(LocalDate start) {
            byDate.headMap(start, false).values().forEach(last::putAll);
        }

        private List<RobinhoodCashIoLiveAccountDto> asOf(LocalDate date) {
            Map<String, BigDecimal> thatDay = byDate.get(date);
            if (thatDay != null) {
                last.putAll(thatDay);
            }
            List<RobinhoodCashIoLiveAccountDto> out = new ArrayList<>();
            for (Map.Entry<String, String> e : labels.entrySet()) {
                BigDecimal v = last.get(e.getKey());
                if (v != null) {
                    out.add(new RobinhoodCashIoLiveAccountDto(e.getKey(), e.getValue(), v));
                }
            }
            out.sort(Comparator.comparing(RobinhoodCashIoLiveAccountDto::label));
            return out;
        }
    }
}
