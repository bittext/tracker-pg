package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodSelectiveTrade;
import com.svp.tracker.finance.dto.RhDailyTrackerAiInsightStatusDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeAiInsightDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeCalendarDayDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeCalendarDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeEntryDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeLedgerDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeRequestDto;
import com.svp.tracker.finance.dto.RobinhoodSelectiveTradeStatsDto;
import com.svp.tracker.finance.repository.RobinhoodSelectiveTradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodSelectiveTradeService {

    private static final String OUT_WORKED = "WORKED";
    private static final String OUT_DIDNT = "DIDNT";
    private static final String OUT_MIXED = "MIXED";

    private static final String SYSTEM_PROMPT =
            """
            You are a trading coach reviewing selective trades the trader chose to journal.
            Each entry has an outcome (WORKED = went as hoped, DIDNT = did not, MIXED = partial) plus an optional symbol and short note.
            There is no dollar P&L — never invent profits or losses.
            Focus on patterns in outcomes, how often they take these trades, note themes, and concrete ways to improve the success rate.
            Return ONLY a JSON object with this exact shape:
            {
              "summary": "1-2 sentence headline about the period",
              "trends": ["...", "..."],
              "frequencyNotes": ["...", "..."],
              "improvements": ["...", "..."],
              "nextActions": ["...", "..."]
            }
            Use 2-5 items per array. Ground every claim in the provided entries and stats.
            """;

    private final CurrentUserService currentUser;
    private final RobinhoodSelectiveTradeRepository repository;
    private final RobinhoodRhDailyTrackerProperties dailyTrackerProps;
    private final RhDailyTrackerOpenAiClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public RhDailyTrackerAiInsightStatusDto aiStatus() {
        var ai = dailyTrackerProps.ai();
        return new RhDailyTrackerAiInsightStatusDto(ai.enabled(), ai.configured());
    }

    @Transactional(readOnly = true)
    public RobinhoodSelectiveTradeLedgerDto ledger(int year, Integer month) {
        long uid = currentUser.requireUserId();
        DateWindow window = resolveWindow(year, month);
        List<RobinhoodSelectiveTrade> rows = repository
                .findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                        uid, window.from(), window.to());
        RobinhoodSelectiveTradeStatsDto stats = computeStats(rows, window);
        return new RobinhoodSelectiveTradeLedgerDto(
                year,
                month,
                window.from(),
                window.to(),
                stats,
                rows.stream().map(this::toEntry).toList());
    }

    @Transactional(readOnly = true)
    public RobinhoodSelectiveTradeCalendarDto calendar(int year, Integer month) {
        long uid = currentUser.requireUserId();
        DateWindow window = resolveWindow(year, month);
        List<RobinhoodSelectiveTrade> rows = repository
                .findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                        uid, window.from(), window.to());
        Map<LocalDate, MutableDay> byDay = new LinkedHashMap<>();
        for (RobinhoodSelectiveTrade row : rows) {
            MutableDay day = byDay.computeIfAbsent(row.getActivityDate(), d -> new MutableDay());
            day.count++;
            switch (row.getOutcome()) {
                case OUT_WORKED -> day.worked++;
                case OUT_DIDNT -> day.didnt++;
                case OUT_MIXED -> day.mixed++;
                default -> {
                }
            }
        }
        List<RobinhoodSelectiveTradeCalendarDayDto> days = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new RobinhoodSelectiveTradeCalendarDayDto(
                        e.getKey(), e.getValue().count, e.getValue().worked, e.getValue().didnt, e.getValue().mixed))
                .toList();
        return new RobinhoodSelectiveTradeCalendarDto(year, month, computeStats(rows, window), days);
    }

    @Transactional
    public RobinhoodSelectiveTradeEntryDto create(RobinhoodSelectiveTradeRequestDto body) {
        long uid = currentUser.requireUserId();
        ValidatedRequest req = validate(body);
        Instant now = Instant.now();
        RobinhoodSelectiveTrade row = new RobinhoodSelectiveTrade();
        row.setOwnerUserId(uid);
        apply(row, req);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return toEntry(repository.save(row));
    }

    @Transactional
    public RobinhoodSelectiveTradeEntryDto update(long id, RobinhoodSelectiveTradeRequestDto body) {
        long uid = currentUser.requireUserId();
        RobinhoodSelectiveTrade row = requireOwned(id, uid);
        ValidatedRequest req = validate(body);
        apply(row, req);
        row.setUpdatedAt(Instant.now());
        return toEntry(repository.save(row));
    }

    @Transactional
    public void delete(long id) {
        long uid = currentUser.requireUserId();
        repository.delete(requireOwned(id, uid));
    }

    @Transactional(readOnly = true)
    public RobinhoodSelectiveTradeAiInsightDto analyze(int year, Integer month) {
        var ai = dailyTrackerProps.ai();
        if (!ai.enabled() || !ai.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Selective trade AI requires Daily Tracker AI configuration (TRACKER_FINANCE_RH_DAILY_TRACKER_AI_ENABLED and API key).");
        }
        long uid = currentUser.requireUserId();
        DateWindow window = resolveWindow(year, month);
        List<RobinhoodSelectiveTrade> rows = repository
                .findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                        uid, window.from(), window.to());
        RobinhoodSelectiveTradeStatsDto stats = computeStats(rows, window);
        String periodLabel = month == null ? String.valueOf(year) : YearMonth.of(year, month).toString();
        if (rows.isEmpty()) {
            return new RobinhoodSelectiveTradeAiInsightDto(
                    periodLabel,
                    ai.model(),
                    "No selective trades logged in this period yet — add a few worked / didn’t entries to unlock trend analysis.",
                    List.of("Not enough journaled trades to spot patterns."),
                    List.of("Frequency is zero for this window."),
                    List.of("Log selective trades as they happen so success rate and habits become visible."),
                    List.of("After your next notable trade, add an entry with a one-line note."),
                    stats);
        }
        String userPrompt = buildFactsPrompt(periodLabel, stats, rows);
        String raw = openAiClient.completeJson(SYSTEM_PROMPT, userPrompt, 1200);
        try {
            JsonNode root = objectMapper.readTree(raw);
            return new RobinhoodSelectiveTradeAiInsightDto(
                    periodLabel,
                    ai.model(),
                    textOr(root.path("summary"), "Selective trade review for " + periodLabel),
                    stringList(root.path("trends")),
                    stringList(root.path("frequencyNotes")),
                    stringList(root.path("improvements")),
                    stringList(root.path("nextActions")),
                    stats);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse selective trade AI JSON: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI returned unreadable analysis");
        }
    }

    private String buildFactsPrompt(
            String periodLabel, RobinhoodSelectiveTradeStatsDto stats, List<RobinhoodSelectiveTrade> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("Period: ").append(periodLabel).append('\n');
        sb.append("Stats: total=")
                .append(stats.total())
                .append(", worked=")
                .append(stats.worked())
                .append(", didnt=")
                .append(stats.didnt())
                .append(", mixed=")
                .append(stats.mixed())
                .append(", successRate=")
                .append(stats.successRate() == null ? "n/a" : stats.successRate())
                .append(", distinctDays=")
                .append(stats.distinctDays())
                .append(", avgPerActiveDay=")
                .append(stats.avgPerActiveDay())
                .append(", avgPerMonthInPeriod=")
                .append(stats.avgPerMonthInPeriod())
                .append('\n');
        Map<String, Long> bySymbol = rows.stream()
                .filter(r -> r.getSymbol() != null && !r.getSymbol().isBlank())
                .collect(Collectors.groupingBy(RobinhoodSelectiveTrade::getSymbol, Collectors.counting()));
        if (!bySymbol.isEmpty()) {
            sb.append("Symbol counts: ").append(bySymbol).append('\n');
        }
        sb.append("Entries (newest first, max 80):\n");
        int n = 0;
        for (RobinhoodSelectiveTrade row : rows) {
            if (n++ >= 80) {
                sb.append("… truncated ").append(rows.size() - 80).append(" more\n");
                break;
            }
            sb.append("- ")
                    .append(row.getActivityDate())
                    .append(" | ")
                    .append(row.getOutcome())
                    .append(" | ")
                    .append(row.getSymbol() == null || row.getSymbol().isBlank() ? "(no symbol)" : row.getSymbol())
                    .append(" | ")
                    .append(row.getNote() == null || row.getNote().isBlank() ? "(no note)" : row.getNote().trim())
                    .append('\n');
        }
        return sb.toString();
    }

    private RobinhoodSelectiveTrade requireOwned(long id, long uid) {
        return repository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selective trade not found"));
    }

    private static void apply(RobinhoodSelectiveTrade row, ValidatedRequest req) {
        row.setActivityDate(req.date());
        row.setSymbol(req.symbol());
        row.setOutcome(req.outcome());
        row.setNote(req.note());
        row.setAccountSuffix(req.suffix());
    }

    private ValidatedRequest validate(RobinhoodSelectiveTradeRequestDto body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (body.activityDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activityDate is required");
        }
        String outcome = normalizeOutcome(body.outcome());
        String symbol = body.symbol() == null ? null : body.symbol().trim().toUpperCase(Locale.ROOT);
        if (symbol != null && symbol.isEmpty()) {
            symbol = null;
        }
        if (symbol != null && symbol.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol too long");
        }
        String note = body.note() == null ? null : body.note().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        String suffix = body.accountSuffix() == null ? null : body.accountSuffix().replaceAll("[^0-9]", "");
        if (suffix != null && suffix.isEmpty()) {
            suffix = null;
        }
        return new ValidatedRequest(body.activityDate(), symbol, outcome, note, suffix);
    }

    private static String normalizeOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome is required");
        }
        String o = raw.trim().toUpperCase(Locale.ROOT);
        return switch (o) {
            case "WORKED", "WIN", "WON", "SUCCESS", "YES" -> OUT_WORKED;
            case "DIDNT", "DIDN'T", "DID_NOT", "LOSS", "LOST", "FAIL", "NO" -> OUT_DIDNT;
            case "MIXED", "SCRATCH", "PARTIAL" -> OUT_MIXED;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "outcome must be WORKED, DIDNT, or MIXED");
        };
    }

    private RobinhoodSelectiveTradeEntryDto toEntry(RobinhoodSelectiveTrade row) {
        return new RobinhoodSelectiveTradeEntryDto(
                row.getId(),
                row.getActivityDate(),
                row.getSymbol(),
                row.getOutcome(),
                row.getNote(),
                row.getAccountSuffix(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static RobinhoodSelectiveTradeStatsDto computeStats(
            List<RobinhoodSelectiveTrade> rows, DateWindow window) {
        int worked = 0;
        int didnt = 0;
        int mixed = 0;
        for (RobinhoodSelectiveTrade row : rows) {
            switch (row.getOutcome()) {
                case OUT_WORKED -> worked++;
                case OUT_DIDNT -> didnt++;
                case OUT_MIXED -> mixed++;
                default -> {
                }
            }
        }
        int total = rows.size();
        int decisive = worked + didnt;
        BigDecimal successRate = decisive == 0
                ? null
                : BigDecimal.valueOf(worked)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(decisive), 1, RoundingMode.HALF_UP);
        long distinctDays = rows.stream().map(RobinhoodSelectiveTrade::getActivityDate).distinct().count();
        BigDecimal avgPerActiveDay = distinctDays == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(total)
                        .divide(BigDecimal.valueOf(distinctDays), 2, RoundingMode.HALF_UP);
        long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(window.from()), YearMonth.from(window.to())) + 1);
        BigDecimal avgPerMonth = BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        return new RobinhoodSelectiveTradeStatsDto(
                total, worked, didnt, mixed, successRate, distinctDays, avgPerActiveDay, avgPerMonth);
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

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) {
                String t = n.asText("").trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        if (out.isEmpty()) {
            out.add("No items returned.");
        }
        return out;
    }

    private static String textOr(JsonNode node, String fallback) {
        String t = node == null ? "" : node.asText("").trim();
        return t.isEmpty() ? fallback : t;
    }

    private record DateWindow(LocalDate from, LocalDate to) {}

    private record ValidatedRequest(
            LocalDate date, String symbol, String outcome, String note, String suffix) {}

    private static final class MutableDay {
        int count;
        int worked;
        int didnt;
        int mixed;
    }
}
