package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceCompanyResearch;
import com.svp.tracker.finance.domain.FinanceCompanyResearchNote;
import com.svp.tracker.finance.dto.CompanyEarningsCalendarDto;
import com.svp.tracker.finance.dto.CompanyEarningsEventDto;
import com.svp.tracker.finance.dto.CompanyEarningsHistoryRowDto;
import com.svp.tracker.finance.dto.CompanyQuoteSnapshotDto;
import com.svp.tracker.finance.dto.CompanyResearchCardDto;
import com.svp.tracker.finance.dto.CompanyResearchDetailDto;
import com.svp.tracker.finance.dto.CompanyResearchFundamentalsDto;
import com.svp.tracker.finance.dto.CompanyResearchListDto;
import com.svp.tracker.finance.dto.CompanyResearchNoteDto;
import com.svp.tracker.finance.dto.CompanyResearchNoteRequestDto;
import com.svp.tracker.finance.dto.CompanyResearchUpdateRequestDto;
import com.svp.tracker.finance.dto.CompanyResearchUpsertRequestDto;
import com.svp.tracker.finance.dto.StockNewsDto;
import com.svp.tracker.finance.repository.FinanceCompanyResearchNoteRepository;
import com.svp.tracker.finance.repository.FinanceCompanyResearchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
public class CompanyResearchService {

    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final Set<String> STATUSES =
            Set.of("WATCHING", "CONSIDERING", "BOUGHT", "PASSED", "REVISIT");
    private static final int NEWS_LIMIT = 30;

    private final CurrentUserService currentUser;
    private final FinanceCompanyResearchRepository researchRepository;
    private final FinanceCompanyResearchNoteRepository noteRepository;
    private final NasdaqEarningsService nasdaqEarningsService;
    private final StockNewsService stockNewsService;
    private final AlphaVantageOverviewService alphaVantageOverviewService;

    @Transactional(readOnly = true)
    public CompanyEarningsCalendarDto earningsCalendar(
            LocalDate from, Integer days, Long minMarketCap, boolean cacheOnly) {
        long uid = currentUser.requireUserId();
        LocalDate start = from != null ? from : LocalDate.now(EASTERN);
        int span = days == null ? 7 : Math.max(1, Math.min(days, 31));
        NasdaqEarningsService.CalendarSlice slice = nasdaqEarningsService.calendarSlice(start, span, cacheOnly);
        Map<String, FinanceCompanyResearch> watched = indexWatched(
                uid, slice.events().stream().map(CompanyEarningsEventDto::symbol).collect(Collectors.toSet()));

        List<CompanyEarningsEventDto> events = new ArrayList<>();
        for (CompanyEarningsEventDto e : slice.events()) {
            if (minMarketCap != null
                    && minMarketCap > 0
                    && (e.marketCapValue() == null || e.marketCapValue() < minMarketCap)) {
                continue;
            }
            FinanceCompanyResearch card = watched.get(e.symbol());
            events.add(new CompanyEarningsEventDto(
                    e.reportDate(),
                    e.symbol(),
                    e.companyName(),
                    e.marketCap(),
                    e.marketCapValue(),
                    e.fiscalQuarterEnding(),
                    e.epsForecast(),
                    e.lastYearEps(),
                    e.lastYearReportDate(),
                    e.timing(),
                    card != null,
                    card != null ? card.getDecisionStatus() : null));
        }
        String source = cacheOnly
                ? (slice.partial() ? "nasdaq-buffer" : "nasdaq-cache")
                : "nasdaq";
        if (!cacheOnly) {
            // Prefetch neighboring months into the server buffer so month navigation stays snappy.
            LocalDate prev = start.minusMonths(1);
            LocalDate next = start.plusMonths(1);
            nasdaqEarningsService.warmCalendarAsync(prev.withDayOfMonth(1), prev.lengthOfMonth());
            nasdaqEarningsService.warmCalendarAsync(next.withDayOfMonth(1), next.lengthOfMonth());
        }
        return new CompanyEarningsCalendarDto(
                start, start.plusDays(span - 1L), span, events, source, slice.partial());
    }

    @Transactional(readOnly = true)
    public CompanyResearchListDto list(String q, String status, Integer earningsWithinDays) {
        long uid = currentUser.requireUserId();
        List<FinanceCompanyResearch> rows;
        String query = clean(q);
        if (!query.isBlank()) {
            rows = researchRepository.search(uid, query);
        } else if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            rows = researchRepository.findByOwnerUserIdAndDecisionStatusOrderByUpdatedAtDesc(
                    uid, normalizeStatus(status));
        } else {
            rows = researchRepository.findByOwnerUserIdOrderByUpdatedAtDesc(uid);
        }

        if (earningsWithinDays != null && earningsWithinDays > 0) {
            LocalDate today = LocalDate.now(EASTERN);
            LocalDate end = today.plusDays(earningsWithinDays);
            rows = rows.stream()
                    .filter(r -> r.getNextEarningsDate() != null
                            && !r.getNextEarningsDate().isBefore(today)
                            && !r.getNextEarningsDate().isAfter(end))
                    .toList();
        }

        List<CompanyResearchCardDto> cards = rows.stream().map(this::toCard).toList();
        return new CompanyResearchListDto(cards, cards.size());
    }

    @Transactional
    public CompanyResearchCardDto upsert(CompanyResearchUpsertRequestDto req) {
        long uid = currentUser.requireUserId();
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String symbol = NasdaqEarningsService.normalizeSymbol(req.symbol());
        if (symbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbol is required");
        }
        FinanceCompanyResearch row = researchRepository
                .findByOwnerUserIdAndSymbol(uid, symbol)
                .orElseGet(() -> {
                    FinanceCompanyResearch n = new FinanceCompanyResearch();
                    n.setOwnerUserId(uid);
                    n.setSymbol(symbol);
                    return n;
                });
        applyUpsert(row, req);
        enrichEarningsFromLive(row);
        return toCard(researchRepository.save(row));
    }

    @Transactional
    public CompanyResearchCardDto update(String symbolRaw, CompanyResearchUpdateRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceCompanyResearch row = requireOwned(uid, symbolRaw);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (req.companyName() != null) {
            row.setCompanyName(trimTo(req.companyName(), 256));
        }
        if (req.decisionStatus() != null && !req.decisionStatus().isBlank()) {
            row.setDecisionStatus(normalizeStatus(req.decisionStatus()));
        }
        if (req.tags() != null) {
            row.setTags(joinTags(req.tags()));
        }
        if (req.thesis() != null) {
            row.setThesis(req.thesis().trim());
        }
        return toCard(researchRepository.save(row));
    }

    @Transactional
    public void delete(String symbolRaw) {
        long uid = currentUser.requireUserId();
        FinanceCompanyResearch row = requireOwned(uid, symbolRaw);
        researchRepository.delete(row);
    }

    @Transactional
    public CompanyResearchDetailDto detail(String symbolRaw, boolean includeNews) {
        long uid = currentUser.requireUserId();
        FinanceCompanyResearch row = requireOwned(uid, symbolRaw);
        row.setLastViewedAt(Instant.now());
        researchRepository.save(row);

        CompanyQuoteSnapshotDto quote = nasdaqEarningsService.quote(row.getSymbol());
        if (quote != null) {
            if ((row.getCompanyName() == null || row.getCompanyName().isBlank())
                    && quote.companyName() != null
                    && !quote.companyName().isBlank()) {
                row.setCompanyName(trimTo(quote.companyName(), 256));
            }
            LocalDate next = nasdaqEarningsService.parseUpcomingEarningsDate(quote.upcomingEarningsMessage());
            if (next != null) {
                row.setNextEarningsDate(next);
            }
            researchRepository.save(row);
        }

        List<CompanyEarningsHistoryRowDto> history = nasdaqEarningsService.earningsHistory(row.getSymbol());
        StockNewsDto news = null;
        StockNewsDto yahooNews = null;
        if (includeNews) {
            try {
                news = stockNewsService.fetchAggregatedNews(row.getSymbol(), row.getCompanyName(), NEWS_LIMIT);
            } catch (Exception e) {
                log.warn("News load failed for {}: {}", row.getSymbol(), e.toString());
            }
            try {
                yahooNews = stockNewsService.fetchYahooNews(row.getSymbol(), row.getCompanyName(), NEWS_LIMIT);
            } catch (Exception e) {
                log.warn("Yahoo news load failed for {}: {}", row.getSymbol(), e.toString());
            }
        }
        List<CompanyResearchNoteDto> notes = noteRepository
                .findByResearchIdAndOwnerUserIdOrderByCreatedAtDesc(row.getId(), uid)
                .stream()
                .map(n -> toNote(n, row.getSymbol()))
                .toList();
        CompanyResearchFundamentalsDto fundamentals = null;
        try {
            fundamentals = alphaVantageOverviewService.overview(row.getSymbol());
        } catch (Exception e) {
            log.warn("Fundamentals load failed for {}: {}", row.getSymbol(), e.toString());
        }
        if (fundamentals != null
                && (row.getCompanyName() == null || row.getCompanyName().isBlank())
                && fundamentals.name() != null
                && !fundamentals.name().isBlank()) {
            row.setCompanyName(trimTo(fundamentals.name(), 256));
            researchRepository.save(row);
        }
        return new CompanyResearchDetailDto(toCard(row), quote, history, news, yahooNews, notes, fundamentals);
    }

    @Transactional(readOnly = true)
    public List<CompanyResearchNoteDto> listNotes(String symbolRaw) {
        long uid = currentUser.requireUserId();
        FinanceCompanyResearch row = requireOwned(uid, symbolRaw);
        return noteRepository.findByResearchIdAndOwnerUserIdOrderByCreatedAtDesc(row.getId(), uid).stream()
                .map(n -> toNote(n, row.getSymbol()))
                .toList();
    }

    @Transactional
    public CompanyResearchNoteDto addNote(String symbolRaw, CompanyResearchNoteRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceCompanyResearch row = requireOwned(uid, symbolRaw);
        String text = req == null ? "" : clean(req.noteText());
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note text is required");
        }
        FinanceCompanyResearchNote note = new FinanceCompanyResearchNote();
        note.setOwnerUserId(uid);
        note.setResearchId(row.getId());
        note.setNoteText(text);
        note.setTags(joinTags(req.tags()));
        note = noteRepository.save(note);
        row.setUpdatedAt(Instant.now());
        researchRepository.save(row);
        return toNote(note, row.getSymbol());
    }

    @Transactional
    public CompanyResearchNoteDto updateNote(long noteId, CompanyResearchNoteRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceCompanyResearchNote note = noteRepository
                .findByIdAndOwnerUserId(noteId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        String text = req == null ? "" : clean(req.noteText());
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note text is required");
        }
        note.setNoteText(text);
        if (req.tags() != null) {
            note.setTags(joinTags(req.tags()));
        }
        note = noteRepository.save(note);
        FinanceCompanyResearch row = researchRepository
                .findByIdAndOwnerUserId(note.getResearchId(), uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research card not found"));
        row.setUpdatedAt(Instant.now());
        researchRepository.save(row);
        return toNote(note, row.getSymbol());
    }

    @Transactional
    public void deleteNote(long noteId) {
        long uid = currentUser.requireUserId();
        FinanceCompanyResearchNote note = noteRepository
                .findByIdAndOwnerUserId(noteId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        long researchId = note.getResearchId();
        noteRepository.delete(note);
        researchRepository.findByIdAndOwnerUserId(researchId, uid).ifPresent(row -> {
            row.setUpdatedAt(Instant.now());
            researchRepository.save(row);
        });
    }

    private void applyUpsert(FinanceCompanyResearch row, CompanyResearchUpsertRequestDto req) {
        String company = clean(req.companyName());
        if (!company.isBlank()) {
            row.setCompanyName(trimTo(company, 256));
        } else if (row.getCompanyName() == null) {
            row.setCompanyName("");
        }
        if (req.decisionStatus() != null && !req.decisionStatus().isBlank()) {
            row.setDecisionStatus(normalizeStatus(req.decisionStatus()));
        } else if (row.getDecisionStatus() == null || row.getDecisionStatus().isBlank()) {
            row.setDecisionStatus("WATCHING");
        }
        if (req.tags() != null) {
            row.setTags(joinTags(req.tags()));
        } else if (row.getTags() == null) {
            row.setTags("");
        }
        if (req.thesis() != null) {
            row.setThesis(req.thesis().trim());
        } else if (row.getThesis() == null) {
            row.setThesis("");
        }
    }

    private void enrichEarningsFromLive(FinanceCompanyResearch row) {
        try {
            CompanyQuoteSnapshotDto quote = nasdaqEarningsService.quote(row.getSymbol());
            if (quote == null) {
                return;
            }
            if ((row.getCompanyName() == null || row.getCompanyName().isBlank())
                    && quote.companyName() != null
                    && !quote.companyName().isBlank()) {
                row.setCompanyName(trimTo(quote.companyName(), 256));
            }
            LocalDate next = nasdaqEarningsService.parseUpcomingEarningsDate(quote.upcomingEarningsMessage());
            if (next != null) {
                row.setNextEarningsDate(next);
            }
        } catch (Exception e) {
            log.debug("Live enrich skipped for {}: {}", row.getSymbol(), e.toString());
        }
    }

    private FinanceCompanyResearch requireOwned(long uid, String symbolRaw) {
        String symbol = NasdaqEarningsService.normalizeSymbol(symbolRaw);
        return researchRepository
                .findByOwnerUserIdAndSymbol(uid, symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not on your Watch list"));
    }

    private Map<String, FinanceCompanyResearch> indexWatched(long uid, Set<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, FinanceCompanyResearch> map = new LinkedHashMap<>();
        for (FinanceCompanyResearch row : researchRepository.findByOwnerUserIdAndSymbolIn(uid, symbols)) {
            map.put(row.getSymbol(), row);
        }
        return map;
    }

    private CompanyResearchCardDto toCard(FinanceCompanyResearch row) {
        int noteCount = (int) noteRepository.countByResearchIdAndOwnerUserId(row.getId(), row.getOwnerUserId());
        return new CompanyResearchCardDto(
                row.getId(),
                row.getSymbol(),
                row.getCompanyName() == null ? "" : row.getCompanyName(),
                row.getDecisionStatus(),
                splitTags(row.getTags()),
                row.getThesis() == null ? "" : row.getThesis(),
                row.getNextEarningsDate(),
                row.getNextEarningsTiming(),
                row.getLastViewedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                noteCount);
    }

    private CompanyResearchNoteDto toNote(FinanceCompanyResearchNote note, String symbol) {
        return new CompanyResearchNoteDto(
                note.getId(),
                note.getResearchId(),
                symbol,
                note.getNoteText(),
                splitTags(note.getTags()),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    private static String normalizeStatus(String status) {
        String s = status.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!STATUSES.contains(s)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "decisionStatus must be one of WATCHING, CONSIDERING, BOUGHT, PASSED, REVISIT");
        }
        return s;
    }

    private static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,|]"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .limit(24)
                .toList();
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String t : tags) {
            if (t == null) {
                continue;
            }
            String v = t.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-_ ]", "");
            if (!v.isBlank()) {
                cleaned.add(v);
            }
            if (cleaned.size() >= 24) {
                break;
            }
        }
        return String.join(",", cleaned);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTo(String value, int max) {
        String v = clean(value);
        return v.length() <= max ? v : v.substring(0, max);
    }
}
