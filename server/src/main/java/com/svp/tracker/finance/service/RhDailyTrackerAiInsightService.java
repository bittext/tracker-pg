package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RhDailyTrackerAiInsight;
import com.svp.tracker.finance.dto.RhDailyTrackerAiFactsDigestDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAiInsightDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAiInsightRequestDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAiInsightStatusDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerDayDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerReportDto;
import com.svp.tracker.finance.repository.RhDailyTrackerAiInsightRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RhDailyTrackerAiInsightService {

    private static final String SYSTEM_PROMPT =
            """
            You are a trading coach analyzing Robinhood Daily Tracker activity facts.
            The facts describe executions (symbol, side, size, order type, timing), account-value trajectory, and cash flows.
            There is NO realized P&L and NO win rate in the data — never invent profits, losses, or win rates.
            Speak factually and encouragingly. Focus on leanings (habits), trends, areas to improve, and concrete next actions.
            Return ONLY a JSON object with this exact shape:
            {
              "summary": "1-2 sentence headline",
              "leanings": ["...", "..."],
              "trends": ["...", "..."],
              "improvements": ["...", "..."],
              "nextActions": ["...", "..."]
            }
            Use 2-5 items per array. Ground every claim in the provided facts.
            """;

    private final RobinhoodRhDailyTrackerProperties props;
    private final RobinhoodRhDailyTrackerService dailyTrackerService;
    private final RhDailyTrackerOpenAiClient openAiClient;
    private final RhDailyTrackerAiInsightRepository insightRepository;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper;

    public RhDailyTrackerAiInsightStatusDto status() {
        var ai = props.ai();
        return new RhDailyTrackerAiInsightStatusDto(ai.enabled(), ai.configured());
    }

    @Transactional
    public RhDailyTrackerAiInsightDto generate(RhDailyTrackerAiInsightRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        ResolvedPeriod period = resolvePeriod(request);
        boolean force = Boolean.TRUE.equals(request.forceRefresh());

        List<RobinhoodRhDailyTrackerDayDto> days = loadDaysInRange(period);

        RhDailyTrackerAiFactsBuilder.FactsBundle bundle;
        try {
            bundle = RhDailyTrackerAiFactsBuilder.build(
                    objectMapper, period.scope(), period.periodKey(), period.periodLabel(), days);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build AI facts: " + e.getMessage());
        }

        long ownerUserId = currentUser.requireUserId();
        if (!force) {
            Optional<RhDailyTrackerAiInsight> cached =
                    insightRepository.findByOwnerUserIdAndScopeAndPeriodKey(
                            ownerUserId, period.scope(), period.periodKey());
            if (cached.isPresent() && bundle.factsHash().equals(cached.get().getFactsHash())) {
                return fromStored(cached.get(), bundle.digest(), true);
            }
        }

        if (!props.ai().configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Daily Tracker AI is not configured. Set TRACKER_FINANCE_RH_DAILY_TRACKER_AI_ENABLED=true and TRACKER_FINANCE_RH_DAILY_TRACKER_AI_API_KEY.");
        }

        String userPrompt = "Period: " + period.periodLabel() + "\nFacts JSON:\n" + bundle.factsJson();
        String completion = openAiClient.completeJson(SYSTEM_PROMPT, userPrompt);
        ParsedInsight parsed = parseCompletion(completion);

        Instant now = Instant.now();
        RhDailyTrackerAiInsight row = insightRepository
                .findByOwnerUserIdAndScopeAndPeriodKey(ownerUserId, period.scope(), period.periodKey())
                .orElseGet(RhDailyTrackerAiInsight::new);
        if (row.getId() == null) {
            row.setOwnerUserId(ownerUserId);
            row.setScope(period.scope());
            row.setPeriodKey(period.periodKey());
            row.setCreatedAt(now);
        }
        row.setFactsHash(bundle.factsHash());
        row.setModel(props.ai().model());
        row.setUpdatedAt(now);
        try {
            row.setInsightJson(objectMapper.writeValueAsString(new StoredInsightPayload(
                    period.scope(),
                    period.periodKey(),
                    period.periodLabel(),
                    now,
                    props.ai().model(),
                    parsed.summary(),
                    parsed.leanings(),
                    parsed.trends(),
                    parsed.improvements(),
                    parsed.nextActions())));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not persist insight");
        }
        insightRepository.save(row);

        return new RhDailyTrackerAiInsightDto(
                period.scope(),
                period.periodKey(),
                period.periodLabel(),
                now,
                props.ai().model(),
                false,
                parsed.summary(),
                parsed.leanings(),
                parsed.trends(),
                parsed.improvements(),
                parsed.nextActions(),
                bundle.digest());
    }

    private RhDailyTrackerAiInsightDto fromStored(
            RhDailyTrackerAiInsight row, RhDailyTrackerAiFactsDigestDto digest, boolean cached) {
        try {
            StoredInsightPayload payload = objectMapper.readValue(row.getInsightJson(), StoredInsightPayload.class);
            return new RhDailyTrackerAiInsightDto(
                    payload.scope() != null ? payload.scope() : row.getScope(),
                    payload.periodKey() != null ? payload.periodKey() : row.getPeriodKey(),
                    payload.periodLabel() != null ? payload.periodLabel() : row.getPeriodKey(),
                    payload.generatedAt() != null ? payload.generatedAt() : row.getUpdatedAt(),
                    payload.model() != null ? payload.model() : row.getModel(),
                    cached,
                    nullToEmpty(payload.summary()),
                    nullToList(payload.leanings()),
                    nullToList(payload.trends()),
                    nullToList(payload.improvements()),
                    nullToList(payload.nextActions()),
                    digest);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cached insight is corrupt; regenerate with forceRefresh");
        }
    }

    private ParsedInsight parseCompletion(String completion) {
        try {
            JsonNode root = objectMapper.readTree(completion);
            return new ParsedInsight(
                    text(root, "summary"),
                    stringList(root, "leanings"),
                    stringList(root, "trends"),
                    stringList(root, "improvements"),
                    stringList(root, "nextActions"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse AI response as JSON");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? "" : n.asText("").trim();
    }

    private static List<String> stringList(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || !n.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : n) {
            String s = item.asText("").trim();
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    private static List<String> nullToList(List<String> v) {
        return v == null ? List.of() : v;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private List<RobinhoodRhDailyTrackerDayDto> loadDaysInRange(ResolvedPeriod period) {
        if (period.start().getYear() == period.end().getYear()) {
            RobinhoodRhDailyTrackerReportDto report =
                    dailyTrackerService.buildReport(period.year(), period.months());
            return filterDays(report.days(), period.start(), period.end());
        }
        // Cross-year week: load Dec of start year + Jan of end year.
        List<RobinhoodRhDailyTrackerDayDto> out = new ArrayList<>();
        out.addAll(filterDays(
                dailyTrackerService
                        .buildReport(period.start().getYear(), List.of(period.start().getMonthValue()))
                        .days(),
                period.start(),
                period.end()));
        out.addAll(filterDays(
                dailyTrackerService
                        .buildReport(period.end().getYear(), List.of(period.end().getMonthValue()))
                        .days(),
                period.start(),
                period.end()));
        return out;
    }

    private static List<RobinhoodRhDailyTrackerDayDto> filterDays(
            List<RobinhoodRhDailyTrackerDayDto> days, LocalDate start, LocalDate end) {
        if (days == null) {
            return List.of();
        }
        return days.stream()
                .filter(d -> d.snapshotDate() != null
                        && !d.snapshotDate().isBefore(start)
                        && !d.snapshotDate().isAfter(end))
                .toList();
    }

    private ResolvedPeriod resolvePeriod(RhDailyTrackerAiInsightRequestDto request) {
        String scopeRaw = request.scope() == null ? "" : request.scope().trim().toUpperCase(Locale.ROOT);
        int year = request.year() != null ? request.year() : LocalDate.now().getYear();
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year out of range");
        }

        return switch (scopeRaw) {
            case "YEAR" -> new ResolvedPeriod(
                    "YEAR",
                    String.valueOf(year),
                    "Year " + year,
                    year,
                    List.of(),
                    LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31));
            case "MONTH" -> {
                int month = request.month() != null ? request.month() : LocalDate.now().getMonthValue();
                if (month < 1 || month > 12) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be 1–12");
                }
                LocalDate start = LocalDate.of(year, month, 1);
                yield new ResolvedPeriod(
                        "MONTH",
                        String.format(Locale.ROOT, "%04d-%02d", year, month),
                        start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                        year,
                        List.of(month),
                        start,
                        start.withDayOfMonth(start.lengthOfMonth()));
            }
            case "WEEK" -> {
                LocalDate anchor = parseDate(request.weekStart(), "weekStart");
                if (anchor == null) {
                    anchor = LocalDate.now();
                }
                LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = monday.plusDays(6);
                int weekYear = monday.get(IsoFields.WEEK_BASED_YEAR);
                int week = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                List<Integer> months = distinctMonths(monday, sunday);
                // Prefer calendar year of Monday for buildReport when week stays in one year.
                int reportYear = monday.getYear();
                yield new ResolvedPeriod(
                        "WEEK",
                        String.format(Locale.ROOT, "%04d-W%02d", weekYear, week),
                        "Week of " + monday + " – " + sunday,
                        reportYear,
                        months.isEmpty() ? List.of(monday.getMonthValue()) : months,
                        monday,
                        sunday);
            }
            case "DAY" -> {
                LocalDate day = parseDate(request.day(), "day");
                if (day == null) {
                    day = LocalDate.now();
                }
                yield new ResolvedPeriod(
                        "DAY",
                        day.toString(),
                        day.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)),
                        day.getYear(),
                        List.of(day.getMonthValue()),
                        day,
                        day);
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "scope must be YEAR, MONTH, WEEK, or DAY");
        };
    }

    private static List<Integer> distinctMonths(LocalDate start, LocalDate end) {
        List<Integer> months = new ArrayList<>();
        LocalDate cursor = start.withDayOfMonth(1);
        LocalDate last = end.withDayOfMonth(1);
        while (!cursor.isAfter(last)) {
            months.add(cursor.getMonthValue());
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private static LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be yyyy-MM-dd");
        }
    }

    private record ResolvedPeriod(
            String scope,
            String periodKey,
            String periodLabel,
            int year,
            List<Integer> months,
            LocalDate start,
            LocalDate end) {}

    private record ParsedInsight(
            String summary,
            List<String> leanings,
            List<String> trends,
            List<String> improvements,
            List<String> nextActions) {}

    private record StoredInsightPayload(
            String scope,
            String periodKey,
            String periodLabel,
            Instant generatedAt,
            String model,
            String summary,
            List<String> leanings,
            List<String> trends,
            List<String> improvements,
            List<String> nextActions) {}
}
