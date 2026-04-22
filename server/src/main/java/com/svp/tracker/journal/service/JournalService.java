package com.svp.tracker.journal.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.domain.JournalAttachment;
import com.svp.tracker.journal.domain.JournalEntry;
import com.svp.tracker.journal.domain.JournalTagDef;
import com.svp.tracker.journal.dto.JournalAttachmentDto;
import com.svp.tracker.journal.dto.JournalCalendarDayDto;
import com.svp.tracker.journal.dto.JournalEntryDto;
import com.svp.tracker.journal.dto.JournalEntryWriteRequest;
import com.svp.tracker.journal.dto.JournalSummaryDto;
import com.svp.tracker.journal.dto.JournalTagDefDto;
import com.svp.tracker.journal.repository.JournalAttachmentRepository;
import com.svp.tracker.journal.repository.JournalEntryRepository;
import com.svp.tracker.journal.repository.JournalTagDefRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class JournalService {

    private static final int CAL_LEVELS = 4;

    private final JournalEntryRepository entryRepository;
    private final JournalTagDefRepository tagDefRepository;
    private final JournalAttachmentRepository attachmentRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;

    public List<JournalTagDefDto> listTagDefinitions() {
        long owner = effectiveOwnerId(null);
        return tagDefRepository.findByOwnerUserIdOrderByNameAsc(owner).stream()
                .map(this::toTagDefDto)
                .toList();
    }

    @Transactional
    public JournalTagDefDto createTag(String name) {
        String n = (name == null ? "" : name).trim();
        if (n.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag name required");
        }
        long owner = currentUser.requireUserId();
        if (tagDefRepository.findByOwnerUserIdAndNameIgnoreCase(owner, n).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag already exists");
        }
        JournalTagDef t = new JournalTagDef();
        t.setOwnerUserId(owner);
        t.setName(n);
        t.setCreatedAt(Instant.now());
        return toTagDefDto(tagDefRepository.save(t));
    }

    @Transactional
    public void deleteTag(long id) {
        JournalTagDef t = tagDefRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Tag not found: " + id));
        assertRowAccess(t.getOwnerUserId());
        tagDefRepository.deleteById(id);
    }

    public List<JournalCalendarDayDto> calendar(int year, int month, Long filterOwnerId) {
        long owner = effectiveOwnerId(filterOwnerId);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<Object[]> raw = entryRepository.countByDayInRange(owner, from, to);
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : raw) {
            LocalDate d = (LocalDate) row[0];
            long c = (Long) row[1];
            byDay.put(d, c);
        }
        int max = byDay.values().stream().mapToInt(Long::intValue).max().orElse(0);
        List<JournalCalendarDayDto> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int c = byDay.getOrDefault(d, 0L).intValue();
            int level = levelForCount(c, max);
            out.add(
                    JournalCalendarDayDto.builder().date(d).entryCount(c).level(level).build());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<JournalEntryDto> listEntriesForDay(LocalDate day, Long filterOwnerId) {
        long owner = effectiveOwnerId(filterOwnerId);
        List<JournalEntry> list = entryRepository.findDayForOwner(owner, day);
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(JournalEntry::getId).toList();
        List<JournalAttachment> atts = attachmentRepository.findByEntry_IdIn(ids);
        Map<Long, List<JournalAttachment>> byEntry =
                atts.stream().collect(Collectors.groupingBy(a -> a.getEntry().getId()));
        return list.stream()
                .map(e -> toDto(e, byEntry.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JournalEntryDto> search(
            LocalDate from,
            LocalDate to,
            String q,
            List<Long> tagIds,
            Long filterOwnerId) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to required");
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        }
        long owner = effectiveOwnerId(filterOwnerId);
        List<JournalEntry> base = entryRepository.findRangeForOwner(owner, from, to);
        List<String> tokens = tokenize(q);
        List<Long> tids = tagIds == null ? List.of() : tagIds.stream().filter(Objects::nonNull).distinct().toList();
        List<JournalEntry> matched = base.stream()
                .filter(e -> matchesTokens(e, tokens))
                .filter(e -> hasAllTagIds(e, tids))
                .toList();
        Set<Long> ids = matched.stream().map(JournalEntry::getId).collect(Collectors.toSet());
        Map<Long, Long> attCounts = countAttachmentsByEntryIds(ids);
        return matched.stream()
                .map(e -> toSearchResultDto(e, toIntCount(attCounts, e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public JournalSummaryDto summarize(
            LocalDate from,
            LocalDate to,
            String q,
            List<Long> tagIds,
            Long filterOwnerId) {
        List<JournalEntryDto> rows = search(from, to, q, tagIds, filterOwnerId);
        Map<String, Long> byMonth = new HashMap<>();
        Map<String, Long> byDay = new HashMap<>();
        for (JournalEntryDto e : rows) {
            String ym = e.getLoggedOn().getYear() + "-"
                    + String.format("%02d", e.getLoggedOn().getMonthValue());
            byMonth.merge(ym, 1L, Long::sum);
            byDay.merge(e.getLoggedOn().toString(), 1L, Long::sum);
        }
        List<JournalSummaryDto.MonthCount> months = byMonth.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> JournalSummaryDto.MonthCount.builder()
                        .yearMonth(e.getKey())
                        .count(e.getValue())
                        .build())
                .toList();
        List<JournalSummaryDto.DayCount> days = byDay.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> JournalSummaryDto.DayCount.builder()
                        .date(e.getKey())
                        .count(e.getValue())
                        .build())
                .toList();
        return JournalSummaryDto.builder()
                .totalCount(rows.size())
                .byMonth(months)
                .byDay(days)
                .build();
    }

    public JournalEntryDto getEntry(long id) {
        JournalEntry e = entryRepository
                .findByIdWithTagsAndAttachments(id)
                .orElseThrow(() -> new NotFoundException("Entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        return toDto(e);
    }

    @Transactional
    public JournalEntryDto create(JournalEntryWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        JournalEntry e = new JournalEntry();
        e.setOwnerUserId(owner);
        e.setLoggedOn(req.getLoggedOn());
        e.setBodyMarkdown(req.getBodyMarkdown().trim().isEmpty() ? " " : req.getBodyMarkdown());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        applyTags(e, req.getTagIds());
        return toDto(entryRepository.save(e));
    }

    @Transactional
    public JournalEntryDto update(long id, JournalEntryWriteRequest req) {
        JournalEntry e = entryRepository
                .findByIdWithTags(id)
                .orElseThrow(() -> new NotFoundException("Entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        e.setLoggedOn(req.getLoggedOn());
        e.setBodyMarkdown(req.getBodyMarkdown().trim().isEmpty() ? " " : req.getBodyMarkdown());
        e.setUpdatedAt(Instant.now());
        e.getTags().clear();
        applyTags(e, req.getTagIds());
        return toDto(entryRepository.save(e));
    }

    @Transactional
    public void deleteEntry(long id) {
        JournalEntry e = entryRepository
                .findByIdWithTagsAndAttachments(id)
                .orElseThrow(() -> new NotFoundException("Entry not found: " + id));
        assertRowAccess(e.getOwnerUserId());
        for (JournalAttachment a : new ArrayList<>(e.getAttachments())) {
            try {
                blobStore.delete(a.getStorageKey());
            } catch (IOException e1) {
                throw new UncheckedIOException(e1);
            }
        }
        entryRepository.deleteById(id);
    }

    @Transactional
    public JournalAttachmentDto addAttachment(long entryId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        JournalEntry entry = entryRepository
                .findById(entryId)
                .orElseThrow(() -> new NotFoundException("Entry not found: " + entryId));
        assertRowAccess(entry.getOwnerUserId());
        String key;
        try (var in = file.getInputStream()) {
            key = blobStore.put(entry.getOwnerUserId(), entry.getId(), in, file.getSize());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        JournalAttachment a = new JournalAttachment();
        a.setEntry(entry);
        a.setStorageKey(key);
        a.setOriginalFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setCreatedAt(Instant.now());
        a = attachmentRepository.save(a);
        return toAttachmentDto(a);
    }

    @Transactional
    public void deleteAttachment(long attachmentId) {
        JournalAttachment a = attachmentRepository
                .findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getEntry().getOwnerUserId());
        try {
            blobStore.delete(a.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        attachmentRepository.deleteById(attachmentId);
    }

    public record AttachmentFile(String contentType, String filename, byte[] body) {}

    public AttachmentFile readAttachment(long attachmentId) {
        JournalAttachment a = attachmentRepository
                .findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getEntry().getOwnerUserId());
        try {
            byte[] body = blobStore.readAllBytes(a.getStorageKey());
            return new AttachmentFile(
                    a.getContentType() != null ? a.getContentType() : "application/octet-stream",
                    a.getOriginalFilename(),
                    body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertRowAccess(Long ownerUserId) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private long effectiveOwnerId(Long filterOwnerId) {
        if (currentUser.isAdmin() && filterOwnerId != null) {
            return filterOwnerId;
        }
        return currentUser.requireUserId();
    }

    private int levelForCount(int count, int maxInMonth) {
        if (count <= 0 || maxInMonth <= 0) {
            return 0;
        }
        int l = (int) Math.ceil((count * 4.0) / maxInMonth);
        return Math.min(CAL_LEVELS, Math.max(1, l));
    }

    private List<String> tokenize(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String[] parts = q.trim().split("\\s+");
        List<String> t = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) {
                t.add(p.toLowerCase());
            }
        }
        return t;
    }

    private boolean matchesTokens(JournalEntry e, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }
        String body = e.getBodyMarkdown().toLowerCase();
        for (String tok : tokens) {
            if (!body.contains(tok)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAllTagIds(JournalEntry e, List<Long> required) {
        if (required.isEmpty()) {
            return true;
        }
        Set<Long> have =
                e.getTags().stream().map(JournalTagDef::getId).collect(Collectors.toSet());
        return have.containsAll(required);
    }

    private static int toIntCount(Map<Long, Long> byEntry, long entryId) {
        long c = byEntry.getOrDefault(entryId, 0L);
        return c > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) c;
    }

    private Map<Long, Long> countAttachmentsByEntryIds(Set<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = attachmentRepository.countByEntryIdIn(entryIds);
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((Long) r[0], (Long) r[1]);
        }
        return m;
    }

    private JournalEntryDto toSearchResultDto(JournalEntry e, int attachmentCount) {
        List<JournalTagDefDto> tagDtos = e.getTags().stream()
                .sorted(Comparator.comparing(JournalTagDef::getName))
                .map(this::toTagDefDto)
                .toList();
        return JournalEntryDto.builder()
                .id(e.getId())
                .ownerUserId(e.getOwnerUserId())
                .loggedOn(e.getLoggedOn())
                .bodyMarkdown(e.getBodyMarkdown())
                .tags(tagDtos)
                .attachmentCount(attachmentCount)
                .attachments(List.of())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private void applyTags(JournalEntry e, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        long owner = e.getOwnerUserId();
        Set<JournalTagDef> defs = new HashSet<>();
        for (Long tid : new HashSet<>(tagIds)) {
            JournalTagDef t = tagDefRepository
                    .findById(tid)
                    .orElseThrow(() -> new NotFoundException("Tag not found: " + tid));
            if (!Objects.equals(t.getOwnerUserId(), owner)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tag: " + tid);
            }
            defs.add(t);
        }
        e.getTags().addAll(defs);
    }

    private JournalTagDefDto toTagDefDto(JournalTagDef t) {
        return JournalTagDefDto.builder()
                .id(t.getId())
                .name(t.getName())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private JournalAttachmentDto toAttachmentDto(JournalAttachment a) {
        return JournalAttachmentDto.builder()
                .id(a.getId())
                .originalFilename(a.getOriginalFilename())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .downloadPath("/api/journal/attachments/" + a.getId() + "/file")
                .build();
    }

    private JournalEntryDto toDto(JournalEntry e) {
        List<JournalAttachment> attList = new ArrayList<>(e.getAttachments());
        attList.sort(Comparator.comparing(JournalAttachment::getId));
        return toDto(e, attList);
    }

    private JournalEntryDto toDto(JournalEntry e, List<JournalAttachment> attachmentList) {
        List<JournalTagDefDto> tagDtos =
                e.getTags().stream().sorted(Comparator.comparing(JournalTagDef::getName)).map(this::toTagDefDto).toList();
        List<JournalAttachmentDto> atts = attachmentList.stream()
                .sorted(Comparator.comparing(JournalAttachment::getId))
                .map(this::toAttachmentDto)
                .toList();
        return JournalEntryDto.builder()
                .id(e.getId())
                .ownerUserId(e.getOwnerUserId())
                .loggedOn(e.getLoggedOn())
                .bodyMarkdown(e.getBodyMarkdown())
                .tags(tagDtos)
                .attachmentCount(atts.size())
                .attachments(atts)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
