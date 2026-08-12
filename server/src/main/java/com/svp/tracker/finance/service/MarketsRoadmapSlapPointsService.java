package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import com.svp.tracker.finance.dto.MarketsRoadmapSlapCashNoteDto;
import com.svp.tracker.finance.dto.MarketsRoadmapSlapCrossingDto;
import com.svp.tracker.finance.dto.MarketsRoadmapSlapPointsDto;
import com.svp.tracker.finance.dto.MarketsRoadmapSlapSeriesPointDto;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import com.svp.tracker.finance.repository.RobinhoodAccountCashIoRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MarketsRoadmapSlapPointsService {

    public static final String DEFAULT_SUFFIX = "3370";
    public static final BigDecimal DEFAULT_STEP = new BigDecimal("50000");

    private final CurrentUserService currentUser;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountCashIoRepository cashIoRepository;

    @Transactional(readOnly = true)
    public MarketsRoadmapSlapPointsDto slapPoints(String accountSuffix, BigDecimal stepAmount) {
        long uid = currentUser.requireUserId();
        String suffix = normalizeSuffix(accountSuffix);
        BigDecimal step = normalizeStep(stepAmount);

        List<RhScheduledTotalRow> rows =
                snapshotRepository.findScheduledTotalsForSuffixAsc(uid, suffix);
        // One point per calendar day (last SCHEDULED if duplicates — query is ASC so last wins)
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        for (RhScheduledTotalRow row : rows) {
            if (row.totalAccountValue() == null) {
                continue;
            }
            byDate.put(row.snapshotDate(), scale(row.totalAccountValue()));
        }
        List<MarketsRoadmapSlapSeriesPointDto> series = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> e : byDate.entrySet()) {
            series.add(new MarketsRoadmapSlapSeriesPointDto(e.getKey(), e.getValue()));
        }

        LocalDate from = series.isEmpty() ? null : series.get(0).date();
        LocalDate to = series.isEmpty() ? null : series.get(series.size() - 1).date();
        BigDecimal latest = series.isEmpty() ? null : series.get(series.size() - 1).totalAccountValue();

        BigDecimal max = BigDecimal.ZERO;
        for (MarketsRoadmapSlapSeriesPointDto p : series) {
            if (p.totalAccountValue().compareTo(max) > 0) {
                max = p.totalAccountValue();
            }
        }

        List<BigDecimal> guides = buildGuides(step, max);
        List<MarketsRoadmapSlapCrossingDto> crossings = detectCrossings(series, guides);

        List<MarketsRoadmapSlapCashNoteDto> cashNotes = cashIoRepository
                .findByOwnerUserIdAndAccountSuffixOrderByActivityDateAscIdAsc(uid, suffix)
                .stream()
                .map(this::toCashNote)
                .toList();

        return new MarketsRoadmapSlapPointsDto(
                suffix,
                "Individual ••••" + suffix,
                step,
                latest,
                to,
                from,
                to,
                series,
                guides,
                crossings,
                cashNotes);
    }

    private MarketsRoadmapSlapCashNoteDto toCashNote(RobinhoodAccountCashIo row) {
        return new MarketsRoadmapSlapCashNoteDto(
                row.getId(),
                row.getActivityDate(),
                row.getDirection(),
                scale(row.getAmount()),
                row.getNote());
    }

    private static List<BigDecimal> buildGuides(BigDecimal step, BigDecimal max) {
        List<BigDecimal> guides = new ArrayList<>();
        if (max.compareTo(BigDecimal.ZERO) <= 0) {
            guides.add(step);
            return guides;
        }
        BigDecimal level = step;
        // Guides through the highest crossed or current high (at least one step above max for headroom)
        BigDecimal ceiling = max.add(step);
        while (level.compareTo(ceiling) <= 0) {
            guides.add(level);
            level = level.add(step);
            if (guides.size() > 80) {
                break;
            }
        }
        return guides;
    }

    private static List<MarketsRoadmapSlapCrossingDto> detectCrossings(
            List<MarketsRoadmapSlapSeriesPointDto> series, List<BigDecimal> guides) {
        List<MarketsRoadmapSlapCrossingDto> out = new ArrayList<>();
        if (series.isEmpty()) {
            return out;
        }
        for (BigDecimal threshold : guides) {
            BigDecimal prior = null;
            for (MarketsRoadmapSlapSeriesPointDto p : series) {
                BigDecimal v = p.totalAccountValue();
                boolean crossed =
                        v.compareTo(threshold) >= 0
                                && (prior == null || prior.compareTo(threshold) < 0);
                if (crossed) {
                    // Only count if we had a prior point below, OR first point already above
                    // (first point above still counts as slap on that day)
                    out.add(new MarketsRoadmapSlapCrossingDto(threshold, p.date(), v, prior));
                    break;
                }
                prior = v;
            }
        }
        return out;
    }

    private static String normalizeSuffix(String raw) {
        String s = raw == null || raw.isBlank() ? DEFAULT_SUFFIX : raw.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) {
            s = DEFAULT_SUFFIX;
        }
        return s;
    }

    private static BigDecimal normalizeStep(BigDecimal step) {
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_STEP;
        }
        if (step.compareTo(new BigDecimal("1000")) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stepAmount must be at least 1000");
        }
        return step.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v.setScale(2, RoundingMode.HALF_UP);
    }
}
