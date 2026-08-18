package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import com.svp.tracker.finance.domain.RobinhoodCashIoYearStart;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.domain.RobinhoodRhSupplementalCashFlow;
import com.svp.tracker.finance.dto.RobinhoodCashIoYtdDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoYtdEventDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoYtdPointDto;
import com.svp.tracker.finance.dto.RobinhoodRhOwnedAccountsDto;
import com.svp.tracker.finance.repository.RobinhoodAccountCashIoRepository;
import com.svp.tracker.finance.repository.RobinhoodCashIoYearStartRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import com.svp.tracker.finance.repository.RobinhoodRhSupplementalCashFlowRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RobinhoodCashIoYtdService {

    static final String DEFAULT_SUFFIX = "3370";
    static final LocalDate DEFAULT_START_DATE = LocalDate.of(2026, 1, 1);
    static final BigDecimal DEFAULT_STARTING_CASH = new BigDecimal("211.76");

    private static final String KIND_START = "START";
    private static final String KIND_INPUT = "INPUT";
    private static final String KIND_OUTPUT = "OUTPUT";
    private static final String KIND_CREDIT = "CREDIT";
    private static final String KIND_DEBIT = "DEBIT";

    private final CurrentUserService currentUser;
    private final RobinhoodCashIoYearStartRepository yearStartRepository;
    private final RobinhoodAccountCashIoRepository cashIoRepository;
    private final RobinhoodRhSupplementalCashFlowRepository supplementalRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public RobinhoodCashIoYtdDto ytd(int year, String accountSuffix) {
        return ytdFor(currentUser.requireUserId(), year, accountSuffix);
    }

    @Transactional(readOnly = true)
    public RobinhoodCashIoYtdDto ytdFor(long uid, int year, String accountSuffix) {
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year is out of range");
        }
        String suffix = normalizeSuffix(accountSuffix);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        RobinhoodCashIoYearStart startRow =
                yearStartRepository.findByOwnerUserIdAndAccountSuffixAndYear(uid, suffix, year).orElse(null);
        LocalDate startDate = startRow != null ? startRow.getStartDate() : from;
        BigDecimal startingCash = startRow != null
                ? scale(startRow.getStartingCash())
                : (year == 2026 && DEFAULT_SUFFIX.equals(suffix) ? DEFAULT_STARTING_CASH : BigDecimal.ZERO.setScale(2));

        List<RawEvent> raw = new ArrayList<>();
        raw.add(new RawEvent(startDate, KIND_START, startingCash, "Cash start · Jan 1 12:00 AM"));

        for (RobinhoodAccountCashIo row :
                cashIoRepository.findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                        uid, suffix, from, to)) {
            boolean out = "OUT".equalsIgnoreCase(row.getDirection());
            raw.add(new RawEvent(
                    row.getActivityDate(),
                    out ? KIND_OUTPUT : KIND_INPUT,
                    scale(row.getAmount()),
                    row.getNote() != null && !row.getNote().isBlank()
                            ? row.getNote()
                            : (out ? "Output" : "Input")));
        }

        Set<LocalDate> csvInterestDays = new HashSet<>();
        for (TxnInterest t : loadCsvInterest(uid, from, to)) {
            boolean debit = "MINT".equals(t.code()) || t.amount().signum() < 0;
            csvInterestDays.add(t.date());
            raw.add(new RawEvent(
                    t.date(),
                    debit ? KIND_DEBIT : KIND_CREDIT,
                    t.amount().abs(),
                    blankTo(t.description(), debit ? "Margin interest" : "Interest received")));
        }

        for (RobinhoodRhSupplementalCashFlow extra :
                supplementalRepository
                        .findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenAndFlowCategoryOrderByActivityDateAscIdAsc(
                                uid, suffix, from, to, "INTEREST")) {
            if (csvInterestDays.contains(extra.getActivityDate())) {
                continue;
            }
            boolean out = "OUT".equalsIgnoreCase(extra.getDirection());
            raw.add(new RawEvent(
                    extra.getActivityDate(),
                    out ? KIND_DEBIT : KIND_CREDIT,
                    scale(extra.getAmount()),
                    blankTo(extra.getDescription(), "Interest received")));
        }

        raw.sort(Comparator.comparing(RawEvent::date).thenComparingInt(e -> kindOrder(e.kind())));

        BigDecimal running = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal inputs = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal outputs = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal credits = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal debits = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<RobinhoodCashIoYtdEventDto> events = new ArrayList<>();
        List<RobinhoodCashIoYtdPointDto> series = new ArrayList<>();

        for (RawEvent e : raw) {
            switch (e.kind()) {
                case KIND_START -> running = e.amount();
                case KIND_INPUT -> {
                    inputs = inputs.add(e.amount());
                    running = running.add(e.amount());
                }
                case KIND_OUTPUT -> {
                    outputs = outputs.add(e.amount());
                    running = running.subtract(e.amount());
                }
                case KIND_CREDIT -> {
                    credits = credits.add(e.amount());
                    running = running.add(e.amount());
                }
                case KIND_DEBIT -> {
                    debits = debits.add(e.amount());
                    running = running.subtract(e.amount());
                }
                default -> {
                }
            }
            running = scale(running);
            events.add(new RobinhoodCashIoYtdEventDto(e.date(), e.kind(), e.amount(), e.note(), running));
            series.add(new RobinhoodCashIoYtdPointDto(e.date(), running));
        }

        RobinhoodRhDailySnapshot live = snapshotRepository
                .findTopByOwnerUserIdAndAccountSuffixAndCaptureKindOrderBySnapshotDateDesc(uid, suffix, "SCHEDULED")
                .orElse(null);

        RobinhoodRhOwnedAccountsDto owned = accountTrackerConfigService.resolveOwnedAccounts(uid);
        String label = "Account ••••" + suffix;
        if (Objects.equals(suffix, owned.individualSuffix())) {
            label = "Individual ••••" + suffix;
        } else if (Objects.equals(suffix, owned.agenticSuffix())) {
            label = "Agentic ••••" + suffix;
        } else if (Objects.equals(suffix, owned.managedSuffix())) {
            label = "Managed ••••" + suffix;
        }
        return new RobinhoodCashIoYtdDto(
                suffix,
                label,
                startDate,
                startDate.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
                        + " "
                        + startDate.getDayOfMonth()
                        + ", "
                        + startDate.getYear()
                        + " 12:00 AM",
                startingCash,
                scale(inputs),
                scale(outputs),
                scale(inputs.subtract(outputs)),
                scale(credits),
                scale(debits),
                scale(running),
                live != null ? scale(live.getTotalAccountValue()) : null,
                live != null ? live.getSnapshotDate() : null,
                events,
                series);
    }

    private List<TxnInterest> loadCsvInterest(long uid, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                """
                SELECT activity_date::date AS d,
                       UPPER(TRIM(trans_code)) AS code,
                       COALESCE(description, '') AS descr,
                       COALESCE(amount, 0) AS amt
                FROM robinhood_transactions
                WHERE owner_user_id = ?
                  AND activity_date >= ?
                  AND activity_date <= ?
                  AND UPPER(TRIM(trans_code)) IN ('INT', 'MINT')
                ORDER BY activity_date, trans_code
                """,
                (rs, rowNum) -> new TxnInterest(
                        rs.getObject("d", LocalDate.class),
                        rs.getString("code"),
                        rs.getString("descr"),
                        scale(rs.getBigDecimal("amt"))),
                uid,
                from,
                to);
    }

    private static String normalizeSuffix(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return DEFAULT_SUFFIX;
        }
        if (digits.length() > 8) {
            digits = digits.substring(digits.length() - 8);
        }
        return digits;
    }

    private static int kindOrder(String kind) {
        return switch (kind) {
            case KIND_START -> 0;
            case KIND_CREDIT -> 1;
            case KIND_INPUT -> 2;
            case KIND_OUTPUT -> 3;
            case KIND_DEBIT -> 4;
            default -> 5;
        };
    }

    private static String blankTo(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static BigDecimal scale(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private record RawEvent(LocalDate date, String kind, BigDecimal amount, String note) {}

    private record TxnInterest(LocalDate date, String code, String description, BigDecimal amount) {}
}
