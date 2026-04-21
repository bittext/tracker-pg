package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.ManagementProperties;
import com.svp.tracker.management.domain.ManagementDayOneAttachment;
import com.svp.tracker.management.domain.ManagementDayOneLog;
import com.svp.tracker.management.domain.ManagementDayOneTagDef;
import com.svp.tracker.management.dto.DayOneCalendarDayDto;
import com.svp.tracker.management.dto.DayOneCountsDto;
import com.svp.tracker.management.dto.ManagementDayOneAttachmentDto;
import com.svp.tracker.management.dto.ManagementDayOneEntryWriteRequest;
import com.svp.tracker.management.dto.ManagementDayOneLogDto;
import com.svp.tracker.management.dto.ManagementDayOneTagDefDto;
import com.svp.tracker.management.repo.ManagementDayOneAttachmentRepository;
import com.svp.tracker.management.repo.ManagementDayOneLogRepository;
import com.svp.tracker.management.repo.ManagementDayOneTagDefRepository;
import com.svp.tracker.management.spec.ManagementDayOneSpecifications;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementDayOneService {

    private static final String ATTACH_DOWNLOAD_PREFIX = "/api/management/day-one/attachments/";

    private final ManagementDayOneLogRepository logRepo;
    private final ManagementDayOneTagDefRepository tagDefRepo;
    private final ManagementDayOneAttachmentRepository attachmentRepo;
    private final ManagementDayOneAttachmentStorage attachmentStorage;
    private final CurrentUserService currentUser;
    private final ManagementProperties managementProperties;

    @Transactional(readOnly = true)
    public List<ManagementDayOneTagDefDto> listTagDefinitions() {
        return tagDefRepo.findAll(Sort.by("name").ascending()).stream()
                .map(this::toTagDefDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ManagementDayOneLogDto> searchEntries(
            LocalDate from,
            LocalDate to,
            String q,
            List<Long> tagIds,
            Long ownerUserIdParam) {
        boolean admin = currentUser.isAdmin();
        long uid = currentUser.requireUserId();
        Long filterOwner = admin ? ownerUserIdParam : null;

        Specification<ManagementDayOneLog> spec = Specification.allOf(
                ManagementDayOneSpecifications.ownerScope(admin, uid, filterOwner),
                ManagementDayOneSpecifications.loggedBetween(from, to),
                ManagementDayOneSpecifications.textContains(q),
                ManagementDayOneSpecifications.hasAllTags(tagIds == null ? List.of() : tagIds));

        Sort sort = Sort.by(Sort.Direction.DESC, "loggedOn").and(Sort.by(Sort.Direction.DESC, "id"));
        return logRepo.findAll(spec, sort).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ManagementDayOneLogDto> listMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return searchEntries(ym.atDay(1), ym.atEndOfMonth(), null, null, null);
    }

    /**
     * Legacy single-slot behaviour for PUT /api/management/day-one: updates the newest line for that
     * calendar day, or creates one if none exist.
     */
    @Transactional
    public ManagementDayOneLogDto legacyUpsertSingleLinePerDay(ManagementDayOneEntryWriteRequest req) {
        Long owner = currentUser.requireUserId();
        LocalDate day = req.getLoggedOn();
        List<ManagementDayOneLog> rows = logRepo.findByOwnerUserIdAndLoggedOnOrderByIdDesc(owner, day);
        if (!rows.isEmpty()) {
            return update(rows.get(0).getId(), req);
        }
        return create(req);
    }

    @Transactional(readOnly = true)
    public List<DayOneCalendarDayDto> calendar(int year, int month, Long ownerUserIdParam) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        boolean admin = currentUser.isAdmin();
        long uid = currentUser.requireUserId();
        Long filterOwner = admin ? ownerUserIdParam : null;

        Map<LocalDate, Long> counts = new HashMap<>();
        if (admin && filterOwner == null) {
            for (Object[] row : logRepo.countGroupedByDayAll(from, to)) {
                counts.put((LocalDate) row[0], (Long) row[1]);
            }
        } else {
            long effectiveOwner = filterOwner != null ? filterOwner : uid;
            for (Object[] row : logRepo.countGroupedByDayForOwner(effectiveOwner, from, to)) {
                counts.put((LocalDate) row[0], (Long) row[1]);
            }
        }

        List<DayOneCalendarDayDto> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int n = counts.getOrDefault(d, 0L).intValue();
            out.add(DayOneCalendarDayDto.builder()
                    .date(d)
                    .entryCount(n)
                    .level(levelFromCount(n))
                    .build());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public DayOneCountsDto counts(Integer year, Integer month, Integer day, Long ownerUserIdParam) {
        if (year == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year is required");
        }
        boolean admin = currentUser.isAdmin();
        long uid = currentUser.requireUserId();
        Long filterOwner = admin ? ownerUserIdParam : null;

        LocalDate yStart = LocalDate.of(year, 1, 1);
        LocalDate yEnd = LocalDate.of(year, 12, 31);
        long yearTotal = countInRange(filterOwner, yStart, yEnd);

        long monthTotal = 0;
        if (month != null && month >= 1 && month <= 12) {
            YearMonth ym = YearMonth.of(year, month);
            monthTotal = countInRange(filterOwner, ym.atDay(1), ym.atEndOfMonth());
        }

        long dayTotal = 0;
        if (month != null && day != null && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
            LocalDate d = safeDay(year, month, day);
            dayTotal = countForDay(filterOwner, d);
        }

        return DayOneCountsDto.builder()
                .year(year)
                .month(month)
                .day(day)
                .entriesInYear(yearTotal)
                .entriesInMonth(monthTotal)
                .entriesOnSelectedDay(dayTotal)
                .build();
    }

    private long countInRange(Long filterOwner, LocalDate from, LocalDate to) {
        boolean admin = currentUser.isAdmin();
        if (admin && filterOwner == null) {
            return logRepo.countByLoggedOnBetween(from, to);
        }
        long owner = filterOwner != null ? filterOwner : currentUser.requireUserId();
        return logRepo.countByOwnerUserIdAndLoggedOnBetween(owner, from, to);
    }

    private long countForDay(Long filterOwner, LocalDate day) {
        boolean admin = currentUser.isAdmin();
        if (admin && filterOwner == null) {
            return logRepo.countByLoggedOn(day);
        }
        long owner = filterOwner != null ? filterOwner : currentUser.requireUserId();
        return logRepo.countByOwnerUserIdAndLoggedOn(owner, day);
    }

    private static LocalDate safeDay(int year, int month, int day) {
        YearMonth ym = YearMonth.of(year, month);
        int last = ym.lengthOfMonth();
        int d = Math.min(day, last);
        return LocalDate.of(year, month, d);
    }

    private static int levelFromCount(int n) {
        if (n <= 0) {
            return 0;
        }
        return Math.min(4, n);
    }

    @Transactional
    public ManagementDayOneLogDto create(ManagementDayOneEntryWriteRequest req) {
        long owner = currentUser.requireUserId();
        String text = req.getEntryText().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryText must not be blank");
        }
        Instant now = Instant.now();
        ManagementDayOneLog e = new ManagementDayOneLog();
        e.setOwnerUserId(owner);
        e.setLoggedOn(req.getLoggedOn());
        e.setEntryText(text);
        e.setLocationText(trimToNull(req.getLocationText()));
        e.setWeatherText(trimToNull(req.getWeatherText()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        applyTags(e, req.getTagIds());
        return toDto(logRepo.save(e));
    }

    @Transactional
    public ManagementDayOneLogDto update(long id, ManagementDayOneEntryWriteRequest req) {
        ManagementDayOneLog e = logRepo
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(e.getOwnerUserId());
        String text = req.getEntryText().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryText must not be blank");
        }
        e.setLoggedOn(req.getLoggedOn());
        e.setEntryText(text);
        e.setLocationText(trimToNull(req.getLocationText()));
        e.setWeatherText(trimToNull(req.getWeatherText()));
        e.setUpdatedAt(Instant.now());
        e.getTags().clear();
        applyTags(e, req.getTagIds());
        return toDto(logRepo.save(e));
    }

    @Transactional
    public void delete(long id) {
        ManagementDayOneLog e =
                logRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(e.getOwnerUserId());
        for (ManagementDayOneAttachment a : e.getAttachments()) {
            try {
                Files.deleteIfExists(attachmentStorage.resolveFile(a.getStorageKey()));
            } catch (IOException ignored) {
                // best-effort
            }
        }
        logRepo.delete(e);
    }

    @Transactional
    public ManagementDayOneAttachmentDto addAttachment(long logId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        }
        long max = managementProperties.getDayOneMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file too large");
        }
        ManagementDayOneLog log = logRepo
                .findById(logId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(log.getOwnerUserId());

        String key = attachmentStorage.store(file, log.getOwnerUserId(), logId);
        ManagementDayOneAttachment a = new ManagementDayOneAttachment();
        a.setLog(log);
        a.setStorageKey(key);
        a.setOriginalFilename(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setCreatedAt(Instant.now());
        a = attachmentRepo.save(a);
        return toAttachmentDto(a);
    }

    @Transactional
    public void deleteAttachment(long attachmentId) throws IOException {
        ManagementDayOneAttachment a = attachmentRepo
                .findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(a.getLog().getOwnerUserId());
        Files.deleteIfExists(attachmentStorage.resolveFile(a.getStorageKey()));
        attachmentRepo.delete(a);
    }

    public byte[] readAttachmentBytes(long attachmentId) throws IOException {
        ManagementDayOneAttachment a = attachmentRepo
                .findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(a.getLog().getOwnerUserId());
        return Files.readAllBytes(attachmentStorage.resolveFile(a.getStorageKey()));
    }

    public String attachmentContentType(long attachmentId) {
        ManagementDayOneAttachment a = attachmentRepo
                .findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(a.getLog().getOwnerUserId());
        return a.getContentType() != null ? a.getContentType() : "application/octet-stream";
    }

    public String attachmentFilename(long attachmentId) {
        ManagementDayOneAttachment a = attachmentRepo
                .findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertRowAccess(a.getLog().getOwnerUserId());
        return a.getOriginalFilename();
    }

    private void applyTags(ManagementDayOneLog e, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<ManagementDayOneTagDef> defs = tagDefRepo.findAllById(tagIds).stream()
                .filter(t -> tagIds.contains(t.getId()))
                .collect(Collectors.toSet());
        if (defs.size() != tagIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown tag id");
        }
        e.getTags().addAll(defs);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void assertRowAccess(Long ownerUserId) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private ManagementDayOneTagDefDto toTagDefDto(ManagementDayOneTagDef t) {
        return ManagementDayOneTagDefDto.builder()
                .id(t.getId())
                .name(t.getName())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private ManagementDayOneLogDto toDto(ManagementDayOneLog e) {
        List<ManagementDayOneTagDefDto> tags =
                e.getTags().stream().map(this::toTagDefDto).sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
        List<ManagementDayOneAttachmentDto> atts = e.getAttachments().stream()
                .map(this::toAttachmentDto)
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        return ManagementDayOneLogDto.builder()
                .id(e.getId())
                .ownerUserId(e.getOwnerUserId())
                .loggedOn(e.getLoggedOn())
                .entryText(e.getEntryText())
                .locationText(e.getLocationText())
                .weatherText(e.getWeatherText())
                .tags(tags)
                .attachments(atts)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private ManagementDayOneAttachmentDto toAttachmentDto(ManagementDayOneAttachment a) {
        return ManagementDayOneAttachmentDto.builder()
                .id(a.getId())
                .originalFilename(a.getOriginalFilename())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .downloadPath(ATTACH_DOWNLOAD_PREFIX + a.getId() + "/raw")
                .build();
    }

    @Transactional
    public ManagementDayOneTagDefDto createTag(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        if (tagDefRepo.existsByNameIgnoreCase(n)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tag already exists");
        }
        ManagementDayOneTagDef t = new ManagementDayOneTagDef();
        t.setName(n);
        t.setCreatedAt(Instant.now());
        return toTagDefDto(tagDefRepo.save(t));
    }

    @Transactional
    public void deleteTag(long id) {
        ManagementDayOneTagDef t =
                tagDefRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        tagDefRepo.delete(t);
    }
}
