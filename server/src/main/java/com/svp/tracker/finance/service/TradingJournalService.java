package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.TradingJournalAttachment;
import com.svp.tracker.finance.domain.TradingJournalEntry;
import com.svp.tracker.finance.domain.TradingJournalRef;
import com.svp.tracker.finance.dto.RobinhoodRhDailySnapshotDetailDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerAccountCellDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerDayDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.TradingJournalAccountHoldingsDto;
import com.svp.tracker.finance.dto.TradingJournalAiDraftDto;
import com.svp.tracker.finance.dto.TradingJournalAttachmentDto;
import com.svp.tracker.finance.dto.TradingJournalCalendarDayDto;
import com.svp.tracker.finance.dto.TradingJournalDayDetailDto;
import com.svp.tracker.finance.dto.TradingJournalEntryDto;
import com.svp.tracker.finance.dto.TradingJournalEntrySummaryDto;
import com.svp.tracker.finance.dto.TradingJournalListDto;
import com.svp.tracker.finance.dto.TradingJournalRefDto;
import com.svp.tracker.finance.dto.TradingJournalRefRequestDto;
import com.svp.tracker.finance.dto.TradingJournalUpdateRequestDto;
import com.svp.tracker.finance.repository.TradingJournalAttachmentRepository;
import com.svp.tracker.finance.repository.TradingJournalEntryRepository;
import com.svp.tracker.finance.repository.TradingJournalRefRepository;
import com.svp.tracker.journal.service.JournalBlobStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingJournalService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final Set<String> REF_KINDS = Set.of("SYMBOL", "URL", "NOTE");
    /** Journal wrap focus accounts (trades, cash flows, holdings). */
    private static final Set<String> FOCUS_ACCOUNT_SUFFIXES = Set.of("3370", "3550", "8696");
    private static final long MAX_ATTACHMENT_BYTES = 12L * 1024 * 1024;

    private final CurrentUserService currentUser;
    private final TradingJournalEntryRepository entryRepository;
    private final TradingJournalRefRepository refRepository;
    private final TradingJournalAttachmentRepository attachmentRepository;
    private final RobinhoodRhDailyTrackerService dailyTrackerService;
    private final JournalBlobStore blobStore;
    private final RhDailyTrackerOpenAiClient openAiClient;
    private final RobinhoodRhDailyTrackerProperties dailyTrackerProps;

    @Transactional(readOnly = true)
    public TradingJournalListDto list(Integer year, Integer month, String q) {
        long uid = currentUser.requireUserId();
        int y = year == null ? LocalDate.now(CENTRAL).getYear() : year;
        LocalDate from;
        LocalDate to;
        Integer monthOut = null;
        if (month != null && month >= 1 && month <= 12) {
            YearMonth ym = YearMonth.of(y, month);
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
            monthOut = month;
        } else {
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
        }
        String query = q == null ? "" : q.trim();
        List<TradingJournalEntry> rows =
                query.isBlank()
                        ? entryRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(uid, from, to)
                        : entryRepository.search(uid, from, to, query);
        List<LocalDate> dates = entryRepository.findDates(uid, from, to);
        List<TradingJournalEntrySummaryDto> entries = rows.stream().map(this::toSummary).toList();
        List<TradingJournalCalendarDayDto> calendarDays =
                monthOut == null
                        ? dailyTrackerService.yearCloseChanges(y)
                        : dailyTrackerService.monthCloseChanges(y, monthOut);
        return new TradingJournalListDto(y, monthOut, query, entries, dates, calendarDays);
    }

    @Transactional(readOnly = true)
    public List<LocalDate> journalDates(Integer year, Integer month) {
        long uid = currentUser.requireUserId();
        int y = year == null ? LocalDate.now(CENTRAL).getYear() : year;
        LocalDate from;
        LocalDate to;
        if (month != null && month >= 1 && month <= 12) {
            YearMonth ym = YearMonth.of(y, month);
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        } else {
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
        }
        return entryRepository.findDates(uid, from, to);
    }

    @Transactional
    public TradingJournalDayDetailDto getOrCreateDay(LocalDate snapshotDate) {
        long uid = currentUser.requireUserId();
        requireDate(snapshotDate);
        TradingJournalEntry entry = entryRepository
                .findByOwnerUserIdAndSnapshotDate(uid, snapshotDate)
                .orElseGet(() -> {
                    TradingJournalEntry n = new TradingJournalEntry();
                    n.setOwnerUserId(uid);
                    n.setSnapshotDate(snapshotDate);
                    n.setTitle("");
                    n.setBodyMarkdown("");
                    n.setTags("");
                    return entryRepository.save(n);
                });
        RobinhoodRhDailyTrackerDayDto wrap = dailyTrackerService.dayWrap(snapshotDate);
        boolean ai = dailyTrackerProps.ai().enabled() && dailyTrackerProps.ai().configured();
        return new TradingJournalDayDetailDto(
                snapshotDate, toEntryDto(entry), wrap, ai, focusAccountHoldings(wrap));
    }

    @Transactional(readOnly = true)
    public TradingJournalDayDetailDto getDay(LocalDate snapshotDate) {
        long uid = currentUser.requireUserId();
        requireDate(snapshotDate);
        TradingJournalEntry entry = entryRepository
                .findByOwnerUserIdAndSnapshotDate(uid, snapshotDate)
                .orElse(null);
        RobinhoodRhDailyTrackerDayDto wrap = dailyTrackerService.dayWrap(snapshotDate);
        boolean ai = dailyTrackerProps.ai().enabled() && dailyTrackerProps.ai().configured();
        return new TradingJournalDayDetailDto(
                snapshotDate,
                entry == null ? null : toEntryDto(entry),
                wrap,
                ai,
                focusAccountHoldings(wrap));
    }

    @Transactional
    public TradingJournalEntryDto update(LocalDate snapshotDate, TradingJournalUpdateRequestDto req) {
        long uid = currentUser.requireUserId();
        TradingJournalEntry entry = requireOwnedEntry(uid, snapshotDate);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (req.title() != null) {
            entry.setTitle(trimTo(req.title(), 256));
        }
        if (req.bodyMarkdown() != null) {
            entry.setBodyMarkdown(req.bodyMarkdown());
        }
        if (req.tags() != null) {
            entry.setTags(joinTags(req.tags()));
        }
        if (req.processGrade() != null) {
            entry.setProcessGrade(normalizeGrade(req.processGrade(), "processGrade"));
        }
        if (req.riskGrade() != null) {
            entry.setRiskGrade(normalizeGrade(req.riskGrade(), "riskGrade"));
        }
        return toEntryDto(entryRepository.save(entry));
    }

    @Transactional
    public TradingJournalEntryDto importCallSummary(LocalDate snapshotDate) {
        long uid = currentUser.requireUserId();
        TradingJournalEntry entry = requireOwnedEntry(uid, snapshotDate);
        RobinhoodRhDailyTrackerDayDto wrap = dailyTrackerService.dayWrap(snapshotDate);
        String note = wrap == null || wrap.summaryNote() == null ? "" : wrap.summaryNote().trim();
        if (note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No call-summary note on Daily Tracker for this day");
        }
        String existing = entry.getBodyMarkdown() == null ? "" : entry.getBodyMarkdown().trim();
        String block = "## Call summary (from Daily Tracker)\n\n" + note + "\n";
        if (existing.contains(note)) {
            entry.setLinkedSummaryNote(true);
            return toEntryDto(entryRepository.save(entry));
        }
        entry.setBodyMarkdown(existing.isBlank() ? block : existing + "\n\n" + block);
        entry.setLinkedSummaryNote(true);
        return toEntryDto(entryRepository.save(entry));
    }

    @Transactional
    public TradingJournalEntryDto pinClose(LocalDate snapshotDate) {
        long uid = currentUser.requireUserId();
        TradingJournalEntry entry = requireOwnedEntry(uid, snapshotDate);
        RobinhoodRhDailyTrackerDayDto wrap = dailyTrackerService.dayWrap(snapshotDate);
        if (wrap == null || !wrap.hasScheduledSnapshot()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "9 PM CT scheduled close is not captured yet for this day");
        }
        entry.setHasScheduledClose(true);
        entry.setCloseCombinedTotal(wrap.combinedTotal());
        entry.setCloseCombinedChange(wrap.combinedTotalChangeFromPrevious());
        entry.setClosePulledAt(Instant.now());
        return toEntryDto(entryRepository.save(entry));
    }

    @Transactional
    public void delete(LocalDate snapshotDate) {
        long uid = currentUser.requireUserId();
        TradingJournalEntry entry = requireOwnedEntry(uid, snapshotDate);
        for (TradingJournalAttachment a :
                attachmentRepository.findByEntryIdAndOwnerUserIdOrderByCreatedAtAsc(entry.getId(), uid)) {
            try {
                blobStore.delete(a.getStorageKey());
            } catch (IOException e) {
                log.warn("Failed to delete trading journal blob {}: {}", a.getStorageKey(), e.toString());
            }
        }
        attachmentRepository.deleteByEntryIdAndOwnerUserId(entry.getId(), uid);
        refRepository.deleteByEntryIdAndOwnerUserId(entry.getId(), uid);
        entryRepository.delete(entry);
    }

    @Transactional
    public TradingJournalRefDto addRef(LocalDate snapshotDate, TradingJournalRefRequestDto req) {
        long uid = currentUser.requireUserId();
        TradingJournalEntry entry = requireOwnedEntry(uid, snapshotDate);
        if (req == null || req.kind() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is required");
        }
        String kind = req.kind().trim().toUpperCase(Locale.ROOT);
        if (!REF_KINDS.contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be SYMBOL, URL, or NOTE");
        }
        TradingJournalRef ref = new TradingJournalRef();
        ref.setOwnerUserId(uid);
        ref.setEntryId(entry.getId());
        ref.setKind(kind);
        if ("SYMBOL".equals(kind)) {
            String sym = normalizeSymbol(req.symbol());
            if (sym.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required for SYMBOL refs");
            }
            ref.setSymbol(sym);
            ref.setLabel(trimTo(req.label() == null || req.label().isBlank() ? sym : req.label(), 256));
        } else if ("URL".equals(kind)) {
            String url = clean(req.url());
            if (url.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required for URL refs");
            }
            ref.setUrl(trimTo(url, 1024));
            ref.setLabel(trimTo(req.label() == null || req.label().isBlank() ? url : req.label(), 256));
        } else {
            String label = clean(req.label());
            if (label.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label is required for NOTE refs");
            }
            ref.setLabel(trimTo(label, 256));
        }
        ref = refRepository.save(ref);
        entry.setUpdatedAt(Instant.now());
        entryRepository.save(entry);
        return toRefDto(ref);
    }

    @Transactional
    public void deleteRef(long refId) {
        long uid = currentUser.requireUserId();
        TradingJournalRef ref = refRepository
                .findByIdAndOwnerUserId(refId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reference not found"));
        long entryId = ref.getEntryId();
        refRepository.delete(ref);
        entryRepository.findByIdAndOwnerUserId(entryId, uid).ifPresent(e -> {
            e.setUpdatedAt(Instant.now());
            entryRepository.save(e);
        });
    }

    @Transactional
    public TradingJournalAttachmentDto addAttachment(LocalDate snapshotDate, MultipartFile file) {
        long uid = currentUser.requireUserId();
        TradingJournalEntry entry = requireOwnedEntry(uid, snapshotDate);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment exceeds 12 MB limit");
        }
        String filename = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        try {
            byte[] bytes = file.getBytes();
            Instant capturedAt =
                    JournalImageCaptureTime.resolve(filename, file.getContentType(), bytes);
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                String key = blobStore.put(uid, entry.getId(), in, bytes.length);
                TradingJournalAttachment row = new TradingJournalAttachment();
                row.setOwnerUserId(uid);
                row.setEntryId(entry.getId());
                row.setStorageKey(key);
                row.setOriginalFilename(trimTo(filename, 512));
                row.setContentType(file.getContentType());
                row.setSizeBytes((long) bytes.length);
                row.setCapturedAt(capturedAt);
                row = attachmentRepository.save(row);
                entry.setUpdatedAt(Instant.now());
                entryRepository.save(entry);
                return toAttachmentDto(row);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to store attachment: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteAttachment(long attachmentId) {
        long uid = currentUser.requireUserId();
        TradingJournalAttachment row = attachmentRepository
                .findByIdAndOwnerUserId(attachmentId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        try {
            blobStore.delete(row.getStorageKey());
        } catch (IOException e) {
            log.warn("Failed to delete blob {}: {}", row.getStorageKey(), e.toString());
        }
        long entryId = row.getEntryId();
        attachmentRepository.delete(row);
        entryRepository.findByIdAndOwnerUserId(entryId, uid).ifPresent(e -> {
            e.setUpdatedAt(Instant.now());
            entryRepository.save(e);
        });
    }

    @Transactional(readOnly = true)
    public byte[] readAttachmentBytes(long attachmentId) {
        long uid = currentUser.requireUserId();
        TradingJournalAttachment row = attachmentRepository
                .findByIdAndOwnerUserId(attachmentId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        try {
            return blobStore.readAllBytes(row.getStorageKey());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to read attachment");
        }
    }

    @Transactional(readOnly = true)
    public TradingJournalAttachment requireAttachmentMeta(long attachmentId) {
        long uid = currentUser.requireUserId();
        return attachmentRepository
                .findByIdAndOwnerUserId(attachmentId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }

    @Transactional(readOnly = true)
    public TradingJournalAiDraftDto aiDraft(LocalDate snapshotDate) {
        requireDate(snapshotDate);
        if (!dailyTrackerProps.ai().enabled() || !dailyTrackerProps.ai().configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Trading Journal AI draft requires Daily Tracker AI configuration");
        }
        RobinhoodRhDailyTrackerDayDto wrap = dailyTrackerService.dayWrap(snapshotDate);
        String facts = buildFactsPrompt(snapshotDate, wrap);
        String system =
                """
                You are a trading journal coach. Write a concise markdown daily wrap draft for the trader.
                Include: market/context line, what happened in the book (totals and notable trades), process notes to fill in,
                and 2–3 reflection prompts. Do not invent fills or prices not in the facts. Keep under 350 words.
                """;
        String draft = openAiClient.completeText(system, facts);
        return new TradingJournalAiDraftDto(draft.trim(), true);
    }

    private String buildFactsPrompt(LocalDate date, RobinhoodRhDailyTrackerDayDto wrap) {
        StringBuilder sb = new StringBuilder();
        sb.append("Snapshot date (Central): ").append(date).append('\n');
        if (wrap == null) {
            sb.append("No Daily Tracker captures for this day yet.\n");
            return sb.toString();
        }
        sb.append("Has 9 PM CT scheduled close: ").append(wrap.hasScheduledSnapshot()).append('\n');
        sb.append("Combined total: ").append(money(wrap.combinedTotal())).append('\n');
        sb.append("Change vs prior 9 PM: ").append(money(wrap.combinedTotalChangeFromPrevious())).append('\n');
        sb.append("Period added: ").append(money(wrap.combinedPeriodAdded())).append('\n');
        sb.append("Period removed: ").append(money(wrap.combinedPeriodRemoved())).append('\n');
        if (wrap.summaryNote() != null && !wrap.summaryNote().isBlank()) {
            sb.append("Call summary note:\n").append(wrap.summaryNote().trim()).append("\n\n");
        }
        if (wrap.accounts() != null && !wrap.accounts().isEmpty()) {
            sb.append("Accounts:\n");
            for (var a : wrap.accounts()) {
                sb.append("- ••••")
                        .append(a.accountSuffix())
                        .append(": ")
                        .append(money(a.totalAccountValue()))
                        .append(" (Δ ")
                        .append(money(a.totalChangeFromPrevious()))
                        .append(")\n");
            }
        }
        if (wrap.trades() != null && !wrap.trades().isEmpty()) {
            sb.append("Trades since prior 9 PM:\n");
            for (RobinhoodRhDailyTradeDto t : wrap.trades().stream().limit(24).toList()) {
                sb.append("- ")
                        .append(t.side())
                        .append(' ')
                        .append(t.quantity())
                        .append(' ')
                        .append(t.symbol())
                        .append(" @ ")
                        .append(money(t.averagePrice()))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private TradingJournalEntry requireOwnedEntry(long uid, LocalDate snapshotDate) {
        requireDate(snapshotDate);
        return entryRepository
                .findByOwnerUserIdAndSnapshotDate(uid, snapshotDate)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No journal entry for " + snapshotDate + " — create/open the day first"));
    }

    private TradingJournalEntrySummaryDto toSummary(TradingJournalEntry e) {
        int refs = (int) refRepository.countByEntryIdAndOwnerUserId(e.getId(), e.getOwnerUserId());
        int atts = (int) attachmentRepository.countByEntryIdAndOwnerUserId(e.getId(), e.getOwnerUserId());
        String body = e.getBodyMarkdown() == null ? "" : e.getBodyMarkdown().trim();
        String preview = body.length() <= 160 ? body : body.substring(0, 160) + "…";
        return new TradingJournalEntrySummaryDto(
                e.getId(),
                e.getSnapshotDate(),
                e.getTitle() == null ? "" : e.getTitle(),
                preview,
                splitTags(e.getTags()),
                e.getProcessGrade(),
                e.getRiskGrade(),
                e.isLinkedSummaryNote(),
                e.isHasScheduledClose(),
                e.getCloseCombinedTotal(),
                e.getCloseCombinedChange(),
                e.getClosePulledAt(),
                e.getUpdatedAt(),
                refs,
                atts);
    }

    private TradingJournalEntryDto toEntryDto(TradingJournalEntry e) {
        List<TradingJournalRefDto> refs = refRepository
                .findByEntryIdAndOwnerUserIdOrderByCreatedAtDesc(e.getId(), e.getOwnerUserId())
                .stream()
                .map(this::toRefDto)
                .toList();
        List<TradingJournalAttachmentDto> atts = attachmentRepository
                .findByEntryIdAndOwnerUserIdOrderByCreatedAtAsc(e.getId(), e.getOwnerUserId())
                .stream()
                .map(this::toAttachmentDto)
                .sorted(Comparator.comparing(
                        TradingJournalAttachmentDto::capturedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new TradingJournalEntryDto(
                e.getId(),
                e.getSnapshotDate(),
                e.getTitle() == null ? "" : e.getTitle(),
                e.getBodyMarkdown() == null ? "" : e.getBodyMarkdown(),
                splitTags(e.getTags()),
                e.getProcessGrade(),
                e.getRiskGrade(),
                e.isLinkedSummaryNote(),
                e.isHasScheduledClose(),
                e.getCloseCombinedTotal(),
                e.getCloseCombinedChange(),
                e.getClosePulledAt(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                refs,
                atts);
    }

    private TradingJournalRefDto toRefDto(TradingJournalRef r) {
        return new TradingJournalRefDto(
                r.getId(), r.getKind(), r.getSymbol(), r.getUrl(), r.getLabel(), r.getCreatedAt());
    }

    private TradingJournalAttachmentDto toAttachmentDto(TradingJournalAttachment a) {
        Instant captured = a.getCapturedAt();
        if (captured == null) {
            captured = JournalImageCaptureTime.fromFilename(a.getOriginalFilename());
        }
        if (captured == null) {
            captured = a.getCreatedAt();
        }
        return new TradingJournalAttachmentDto(
                a.getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                "/api/markets/trading-journal/attachments/" + a.getId() + "/file",
                a.getCreatedAt(),
                captured);
    }

    /** Holdings at the scheduled close for journal focus accounts (3370, 3550, 8696). */
    private List<TradingJournalAccountHoldingsDto> focusAccountHoldings(RobinhoodRhDailyTrackerDayDto wrap) {
        if (wrap == null || wrap.accounts() == null || wrap.accounts().isEmpty()) {
            return List.of();
        }
        List<TradingJournalAccountHoldingsDto> out = new ArrayList<>();
        for (RobinhoodRhDailyTrackerAccountCellDto cell : wrap.accounts()) {
            String suffix = cell.accountSuffix() == null ? "" : cell.accountSuffix().trim();
            if (!FOCUS_ACCOUNT_SUFFIXES.contains(suffix)) {
                continue;
            }
            try {
                RobinhoodRhDailySnapshotDetailDto detail =
                        dailyTrackerService.getSnapshotDetail(cell.snapshotId());
                List<RobinhoodRhHoldingDto> holdings = detail.holdings() == null
                        ? List.of()
                        : detail.holdings().stream()
                                .sorted(Comparator.comparing(
                                        h -> h.marketValue() == null ? BigDecimal.ZERO : h.marketValue(),
                                        Comparator.reverseOrder()))
                                .toList();
                String label = detail.label() == null || detail.label().isBlank() ? suffix : detail.label();
                out.add(new TradingJournalAccountHoldingsDto(suffix, label, holdings));
            } catch (Exception e) {
                log.debug("Skip journal holdings for ••••{}: {}", suffix, e.toString());
            }
        }
        return out;
    }

    private static void requireDate(LocalDate snapshotDate) {
        if (snapshotDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "snapshotDate is required");
        }
    }

    private static Integer normalizeGrade(Integer grade, String field) {
        if (grade == null) {
            return null;
        }
        if (grade < 1 || grade > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be 1–5 or omitted");
        }
        return grade;
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9.\\-]", "");
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
        return cleaned.stream().collect(Collectors.joining(","));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTo(String value, int max) {
        String v = clean(value);
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String money(BigDecimal v) {
        return v == null ? "—" : v.toPlainString();
    }
}
