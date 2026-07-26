package com.svp.tracker.management.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.finance.service.RhDailyTrackerOpenAiClient;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.config.ManagementRecordingsProperties;
import com.svp.tracker.management.domain.ManagementRecordingCache;
import com.svp.tracker.management.domain.ManagementRecordingImage;
import com.svp.tracker.management.dto.ManagementRecordingDayDto;
import com.svp.tracker.management.dto.ManagementRecordingDetailDto;
import com.svp.tracker.management.dto.ManagementRecordingImageDto;
import com.svp.tracker.management.dto.ManagementRecordingItemDto;
import com.svp.tracker.management.dto.ManagementRecordingListDto;
import com.svp.tracker.management.dto.ManagementRecordingReprocessDto;
import com.svp.tracker.management.dto.ManagementRecordingTranscriptSegmentDto;
import com.svp.tracker.management.repository.ManagementRecordingCacheRepository;
import com.svp.tracker.management.repository.ManagementRecordingImageRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cloud-backed Just Press Record library. Users upload .m4a files (from the iCloud Drive folder on their device);
 * audio lives in {@link JournalBlobStore} so production Lightsail can play/transcribe without a Mac path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManagementRecordingsService {

    private static final Pattern DAY_FOLDER = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Set<String> AUDIO_EXT = Set.of(".m4a", ".mp3", ".wav", ".webm", ".ogg");
    private static final Set<String> IMAGE_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif");

    private final ManagementRecordingsProperties properties;
    private final ManagementRecordingCacheRepository cacheRepository;
    private final ManagementRecordingImageRepository imageRepository;
    private final CurrentUserService currentUser;
    private final RhDailyTrackerOpenAiClient openAi;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean processing = new AtomicBoolean();

    @Transactional(readOnly = true)
    public ManagementRecordingListDto list(LocalDate dayFilter) {
        if (!properties.configured()) {
            return new ManagementRecordingListDto(
                    false,
                    "cloud",
                    "Recordings are disabled (TRACKER_MANAGEMENT_RECORDINGS_ENABLED).",
                    List.of(),
                    List.of());
        }

        long owner = currentUser.requireUserId();
        List<ManagementRecordingCache> rows = cacheRepository
                .findByOwnerUserIdOrderByRecordedDayDescUpdatedAtDesc(owner)
                .stream()
                .filter(r -> r.getStorageKey() != null && !r.getStorageKey().isBlank())
                .filter(r -> dayFilter == null || dayFilter.equals(r.getRecordedDay()))
                .toList();

        Map<LocalDate, Integer> dayCounts = new HashMap<>();
        List<ManagementRecordingItemDto> items = new ArrayList<>();
        for (ManagementRecordingCache r : rows) {
            if (r.getRecordedDay() != null) {
                dayCounts.merge(r.getRecordedDay(), 1, Integer::sum);
            }
            items.add(toItem(r));
        }

        // Day chips should reflect the full library, not only the filtered day list.
        if (dayFilter != null) {
            dayCounts.clear();
            for (ManagementRecordingCache r : cacheRepository.findByOwnerUserIdOrderByRecordedDayDescUpdatedAtDesc(owner)) {
                if (r.getStorageKey() == null || r.getStorageKey().isBlank() || r.getRecordedDay() == null) {
                    continue;
                }
                dayCounts.merge(r.getRecordedDay(), 1, Integer::sum);
            }
        }

        List<ManagementRecordingDayDto> days = dayCounts.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Integer>comparingByKey().reversed())
                .map(e -> new ManagementRecordingDayDto(e.getKey().toString(), e.getValue()))
                .toList();

        items.sort(Comparator.comparing(
                        ManagementRecordingItemDto::recordedDay, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ManagementRecordingItemDto::displayName));

        String note = items.isEmpty()
                ? "No recordings uploaded yet. Choose the Just Press Record Documents folder from iCloud Drive (date folders of .m4a files) and upload."
                : null;
        return new ManagementRecordingListDto(true, "cloud", note, days, items);
    }

    @Transactional
    public List<ManagementRecordingItemDto> upload(List<MultipartFile> files, List<String> relativePaths) {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recordings are disabled");
        }
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one file is required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        long owner = currentUser.requireUserId();
        List<ManagementRecordingItemDto> out = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file == null || file.isEmpty()) {
                continue;
            }
            String relHint = relativePaths != null && i < relativePaths.size() ? relativePaths.get(i) : null;
            String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "recording.m4a");
            if (!isAudioFilename(originalName) && (relHint == null || !isAudioFilename(relHint))) {
                // Skip non-audio (e.g. .DS_Store when uploading a folder).
                continue;
            }
            if (file.getSize() > max) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        originalName + " exceeds " + max + " bytes");
            }

            String relativePath = normalizeRelativePath(relHint, originalName);
            String displayName = Path.of(relativePath).getFileName().toString();
            LocalDate day = parseDayFromRelativePath(relativePath);

            String key;
            try (var in = file.getInputStream()) {
                key = blobStore.put(owner, 0L, in, file.getSize());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }

            ManagementRecordingCache row = cacheRepository
                    .findByOwnerUserIdAndRelativePath(owner, relativePath)
                    .orElseGet(ManagementRecordingCache::new);

            // Replace prior blob if re-uploading the same relative path.
            if (row.getId() != null && row.getStorageKey() != null && !row.getStorageKey().isBlank()) {
                try {
                    blobStore.delete(row.getStorageKey());
                } catch (IOException ex) {
                    log.warn("Failed deleting old recording blob {}: {}", row.getStorageKey(), ex.toString());
                }
            }

            row.setOwnerUserId(owner);
            row.setRelativePath(relativePath);
            row.setDisplayName(displayName);
            row.setRecordedDay(day);
            row.setFileSizeBytes(file.getSize());
            row.setStorageKey(key);
            row.setContentType(guessContentType(displayName, file.getContentType()));
            row.setOriginalFilename(originalName);
            // Fresh audio invalidates prior transcript/summary.
            row.setTranscript(null);
            row.setTranscriptSource(null);
            row.setTranscriptSegmentsJson(null);
            row.setTranscribedAt(null);
            row.setSummary(null);
            row.setSummarizedAt(null);
            if (properties.autoProcessEnabled()) {
                row.setProcessingStatus("PENDING");
            } else {
                row.setProcessingStatus("IDLE");
            }
            row.setProcessingError(null);
            row.setProcessingStartedAt(null);
            row.setProcessingCompletedAt(null);
            row = cacheRepository.save(row);
            out.add(toItem(row));
        }

        if (out.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No audio files found in the upload (.m4a, .mp3, .wav, …)");
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ManagementRecordingDetailDto detail(String relativePath) {
        return toDetail(requireStored(relativePath));
    }

    @Transactional(readOnly = true)
    public RecordingFile readFile(String relativePath) {
        ManagementRecordingCache row = requireStored(relativePath);
        try {
            byte[] body = blobStore.readAllBytes(row.getStorageKey());
            String ct = row.getContentType() != null && !row.getContentType().isBlank()
                    ? row.getContentType()
                    : guessContentType(row.getDisplayName(), null);
            String fn = row.getDisplayName() != null && !row.getDisplayName().isBlank()
                    ? row.getDisplayName()
                    : (row.getOriginalFilename() != null ? row.getOriginalFilename() : "recording.m4a");
            return new RecordingFile(fn, ct, body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public void delete(String relativePath) {
        ManagementRecordingCache row = requireStored(relativePath);
        deleteImagesForRecording(row);
        try {
            if (row.getStorageKey() != null) {
                blobStore.delete(row.getStorageKey());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cacheRepository.delete(row);
    }

    @Transactional(readOnly = true)
    public List<ManagementRecordingImageDto> listImages(String relativePath) {
        ManagementRecordingCache row = requireStored(relativePath);
        return imageRepository.findByRecordingIdOrderBySortOrderAscIdAsc(row.getId()).stream()
                .map(this::toImageDto)
                .toList();
    }

    @Transactional
    public List<ManagementRecordingImageDto> uploadImages(String relativePath, List<MultipartFile> files) {
        ManagementRecordingCache row = requireStored(relativePath);
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image files provided");
        }
        long owner = currentUser.requireUserId();
        long maxBytes = journalProperties.getMaxAttachmentBytes();
        Integer maxSort = imageRepository.findMaxSortOrder(row.getId());
        int sort = maxSort == null ? 0 : maxSort + 1;
        List<ManagementRecordingImageDto> out = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename() == null ? "image.jpg" : file.getOriginalFilename();
            if (!isImageFilename(originalName)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Not an image file: " + Path.of(originalName).getFileName());
            }
            if (file.getSize() > maxBytes) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds max size of " + maxBytes + " bytes");
            }
            String key;
            try (var in = file.getInputStream()) {
                key = blobStore.put(owner, 0L, in, file.getSize());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            ManagementRecordingImage img = new ManagementRecordingImage();
            img.setRecording(row);
            img.setOwnerUserId(owner);
            img.setStorageKey(key);
            img.setOriginalFilename(Path.of(originalName).getFileName().toString());
            img.setContentType(guessImageContentType(originalName, file.getContentType()));
            img.setSizeBytes(file.getSize());
            img.setSortOrder(sort++);
            img = imageRepository.save(img);
            out.add(toImageDto(img));
        }
        if (out.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image files found in the upload");
        }
        return out;
    }

    @Transactional(readOnly = true)
    public RecordingFile readImage(long imageId) {
        long owner = currentUser.requireUserId();
        ManagementRecordingImage img = imageRepository
                .findByIdAndOwner(imageId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        try {
            byte[] body = blobStore.readAllBytes(img.getStorageKey());
            String ct = img.getContentType() != null && !img.getContentType().isBlank()
                    ? img.getContentType()
                    : guessImageContentType(img.getOriginalFilename(), null);
            String fn = img.getOriginalFilename() == null || img.getOriginalFilename().isBlank()
                    ? "image.jpg"
                    : img.getOriginalFilename();
            return new RecordingFile(fn, ct, body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public void deleteImage(long imageId) {
        long owner = currentUser.requireUserId();
        ManagementRecordingImage img = imageRepository
                .findByIdAndOwner(imageId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        try {
            blobStore.delete(img.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        imageRepository.delete(img);
    }

    private void deleteImagesForRecording(ManagementRecordingCache row) {
        if (row.getId() == null) {
            return;
        }
        for (ManagementRecordingImage img :
                imageRepository.findByRecordingIdOrderBySortOrderAscIdAsc(row.getId())) {
            try {
                blobStore.delete(img.getStorageKey());
            } catch (IOException e) {
                log.warn("Failed deleting recording image blob {}: {}", img.getStorageKey(), e.toString());
            }
            imageRepository.delete(img);
        }
    }

    /**
     * Rename the recording label in Tracker only. Does not change blob storage keys or the user's
     * iCloud Drive / Just Press Record file (upload is a one-way copy).
     */
    @Transactional
    public ManagementRecordingDetailDto rename(String relativePath, String displayName) {
        ManagementRecordingCache row = requireStored(relativePath);
        String cleaned = normalizeDisplayName(displayName, row.getDisplayName());
        row.setDisplayName(cleaned);
        // Keep downloads aligned with the label the user sees.
        row.setOriginalFilename(cleaned);
        row = cacheRepository.save(row);
        return toDetail(row);
    }

    private static String normalizeDisplayName(String requested, String currentDisplayName) {
        String raw = requested == null ? "" : requested.trim();
        if (raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
        }
        // Prevent path traversal / folder injection in the label.
        raw = raw.replace('\\', '/');
        int slash = raw.lastIndexOf('/');
        if (slash >= 0) {
            raw = raw.substring(slash + 1).trim();
        }
        if (raw.isBlank() || raw.equals(".") || raw.equals("..") || raw.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid display name");
        }
        if (raw.length() > 512) {
            raw = raw.substring(0, 512);
        }
        // Preserve audio extension if the user omitted it.
        String current = currentDisplayName == null ? "" : currentDisplayName.trim();
        int curDot = current.lastIndexOf('.');
        String curExt = curDot > 0 ? current.substring(curDot) : "";
        int newDot = raw.lastIndexOf('.');
        if (!curExt.isBlank() && (newDot < 0 || newDot == raw.length() - 1)) {
            raw = raw + curExt;
        }
        if (!isAudioFilename(raw)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Name must keep an audio extension (.m4a, .mp3, .wav, …)");
        }
        return raw;
    }

    @Transactional
    public ManagementRecordingDetailDto transcribe(String relativePath, boolean force) {
        ManagementRecordingCache row = requireStored(relativePath);
        if (!force && row.getTranscript() != null && !row.getTranscript().isBlank()) {
            return toDetail(row);
        }
        Path tmp = writeTempAudio(row);
        try {
            var result = openAi.transcribeAudio(tmp);
            row.setTranscript(result.text());
            row.setTranscriptSource(result.source());
            row.setTranscriptSegmentsJson(serializeSegments(result.segments()));
            row.setTranscribedAt(Instant.now());
            row.setProcessingStatus("READY");
            row.setProcessingError(null);
            row.setProcessingCompletedAt(Instant.now());
            row = cacheRepository.save(row);
            return toDetail(row);
        } finally {
            deleteQuietly(tmp);
        }
    }

    @Transactional
    public ManagementRecordingDetailDto summarize(String relativePath, boolean force) {
        ManagementRecordingCache row = requireStored(relativePath);
        if (row.getTranscript() == null || row.getTranscript().isBlank()) {
            Path tmp = writeTempAudio(row);
            try {
                var result = openAi.transcribeAudio(tmp);
                row.setTranscript(result.text());
                row.setTranscriptSource(result.source());
                row.setTranscriptSegmentsJson(serializeSegments(result.segments()));
                row.setTranscribedAt(Instant.now());
            } finally {
                deleteQuietly(tmp);
            }
        }
        if (!force && row.getSummary() != null && !row.getSummary().isBlank()) {
            row = cacheRepository.save(row);
            return toDetail(row);
        }
        row.setSummary(generateSummary(row));
        row.setSummarizedAt(Instant.now());
        row.setProcessingStatus("READY");
        row.setProcessingError(null);
        row.setProcessingCompletedAt(Instant.now());
        row = cacheRepository.save(row);
        return toDetail(row);
    }

    /**
     * Manually rebuild transcript + summary for one recording.
     * Prefer background PENDING when auto-process is on so the HTTP request returns quickly.
     */
    @Transactional
    public ManagementRecordingDetailDto reprocess(String relativePath) {
        ManagementRecordingCache row = requireStored(relativePath);
        if (properties.autoProcessEnabled()) {
            row.setTranscript(null);
            row.setTranscriptSource(null);
            row.setTranscriptSegmentsJson(null);
            row.setTranscribedAt(null);
            row.setSummary(null);
            row.setSummarizedAt(null);
            row.setProcessingStatus("PENDING");
            row.setProcessingError(null);
            row.setProcessingStartedAt(null);
            row.setProcessingCompletedAt(null);
            row = cacheRepository.save(row);
            return toDetail(row);
        }
        Path tmp = writeTempAudio(row);
        try {
            var result = openAi.transcribeAudio(tmp);
            row.setTranscript(result.text());
            row.setTranscriptSource(result.source());
            row.setTranscriptSegmentsJson(serializeSegments(result.segments()));
            row.setTranscribedAt(Instant.now());
            row.setSummary(generateSummary(row));
            row.setSummarizedAt(Instant.now());
            row.setProcessingStatus("READY");
            row.setProcessingError(null);
            row.setProcessingStartedAt(null);
            row.setProcessingCompletedAt(Instant.now());
            row = cacheRepository.save(row);
            return toDetail(row);
        } catch (Exception e) {
            row.setProcessingStatus("FAILED");
            row.setProcessingError(compactError(e));
            row.setProcessingCompletedAt(Instant.now());
            cacheRepository.save(row);
            if (e instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Reprocess failed: " + compactError(e));
        } finally {
            deleteQuietly(tmp);
        }
    }

    /** Clear any leftover PENDING/PROCESSING rows for this user. */
    @Transactional
    public ManagementRecordingReprocessDto cancelQueue() {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recordings are disabled");
        }
        long owner = currentUser.requireUserId();
        List<ManagementRecordingCache> rows =
                cacheRepository.findByOwnerUserIdOrderByRecordedDayDescUpdatedAtDesc(owner);
        int cleared = 0;
        for (ManagementRecordingCache row : rows) {
            String status = row.getProcessingStatus();
            if (!"PENDING".equals(status) && !"PROCESSING".equals(status)) {
                continue;
            }
            boolean hasTranscript = row.getTranscript() != null && !row.getTranscript().isBlank();
            row.setProcessingStatus(hasTranscript ? "READY" : "IDLE");
            row.setProcessingError(null);
            row.setProcessingStartedAt(null);
            if (hasTranscript && row.getProcessingCompletedAt() == null) {
                row.setProcessingCompletedAt(Instant.now());
            }
            cacheRepository.save(row);
            cleared++;
        }
        return new ManagementRecordingReprocessDto(cleared);
    }

    /**
     * Durable single-file worker. Uploads and V93 leave rows PENDING when auto-process is enabled.
     * Serial processing avoids racing OpenAI limits and keeps large audio files from exhausting memory.
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = 3_000)
    public void processNextPendingRecording() {
        if (!properties.autoProcessEnabled() || !processing.compareAndSet(false, true)) {
            return;
        }
        try {
            recoverStaleProcessingRows();
            cacheRepository
                    .findFirstByProcessingStatusOrderByUpdatedAtAsc("PENDING")
                    .ifPresent(this::processQueuedRecording);
        } catch (Exception e) {
            log.error("Recording background worker failed", e);
        } finally {
            processing.set(false);
        }
    }

    private void recoverStaleProcessingRows() {
        Instant cutoff = Instant.now().minusSeconds(30 * 60);
        for (ManagementRecordingCache row :
                cacheRepository.findByProcessingStatusAndProcessingStartedAtBefore("PROCESSING", cutoff)) {
            row.setProcessingStatus("PENDING");
            row.setProcessingError("Recovered after interrupted background processing");
            row.setProcessingStartedAt(null);
            cacheRepository.save(row);
        }
    }

    private void processQueuedRecording(ManagementRecordingCache row) {
        row.setProcessingStatus("PROCESSING");
        row.setProcessingError(null);
        row.setProcessingStartedAt(Instant.now());
        row.setProcessingCompletedAt(null);
        row = cacheRepository.saveAndFlush(row);

        Path tmp = null;
        try {
            tmp = writeTempAudio(row);
            var result = openAi.transcribeAudio(tmp);
            row.setTranscript(result.text());
            row.setTranscriptSource(result.source());
            row.setTranscriptSegmentsJson(serializeSegments(result.segments()));
            row.setTranscribedAt(Instant.now());
            row.setSummary(generateSummary(row));
            row.setSummarizedAt(Instant.now());
            row.setProcessingStatus("READY");
            row.setProcessingError(null);
            row.setProcessingCompletedAt(Instant.now());
            cacheRepository.save(row);
            log.info(
                    "Background transcript + summary ready for recording id={} path={}",
                    row.getId(),
                    row.getRelativePath());
        } catch (Exception e) {
            row.setProcessingStatus("FAILED");
            row.setProcessingError(compactError(e));
            row.setProcessingCompletedAt(Instant.now());
            cacheRepository.save(row);
            log.warn(
                    "Background transcript + summary failed for recording id={} path={}: {}",
                    row.getId(),
                    row.getRelativePath(),
                    e.toString());
        } finally {
            deleteQuietly(tmp);
        }
    }

    private String generateSummary(ManagementRecordingCache row) {
        String system =
                """
                You summarize personal voice memos from Just Press Record.
                Write a concise summary in plain language: key points, action items, and any dates or names mentioned.
                Use short paragraphs or bullets. Do not invent details that are not in the transcript.
                """;
        String user = "Recording: "
                + row.getDisplayName()
                + " ("
                + row.getRecordedDay()
                + ")\n\nTranscript:\n"
                + row.getTranscript();
        return openAi.completeText(system, user);
    }

    private static String compactError(Exception e) {
        String message = e instanceof ResponseStatusException rse ? rse.getReason() : e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= 1_000 ? message : message.substring(0, 1_000);
    }

    @Transactional(readOnly = true)
    public List<ManagementRecordingItemDto> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be at least 2 characters");
        }
        if (!properties.configured()) {
            return List.of();
        }
        long owner = currentUser.requireUserId();
        return cacheRepository.search(owner, q).stream()
                .filter(r -> r.getStorageKey() != null && !r.getStorageKey().isBlank())
                .map(this::toItem)
                .toList();
    }

    private Path writeTempAudio(ManagementRecordingCache row) {
        try {
            byte[] body = blobStore.readAllBytes(row.getStorageKey());
            String suffix = ".m4a";
            String name = row.getDisplayName() == null ? "" : row.getDisplayName().toLowerCase(Locale.ROOT);
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                suffix = name.substring(dot);
            }
            Path tmp = Files.createTempFile("tracker-rec-", suffix);
            Files.write(tmp, body);
            return tmp;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path tmp) {
        if (tmp == null) {
            return;
        }
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private ManagementRecordingCache requireStored(String relativePath) {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recordings are disabled");
        }
        String path = normalizeRelativePath(relativePath, null);
        long owner = currentUser.requireUserId();
        ManagementRecordingCache row = cacheRepository
                .findByOwnerUserIdAndRelativePath(owner, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording not found"));
        if (row.getStorageKey() == null || row.getStorageKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording audio is missing — re-upload the file");
        }
        return row;
    }

    private static String normalizeRelativePath(String relativePath, String fallbackFilename) {
        String raw = relativePath == null || relativePath.isBlank() ? fallbackFilename : relativePath;
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }
        String normalized = raw.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // Drop a leading folder name if the browser sent Documents/YYYY-MM-DD/...
        if (normalized.toLowerCase(Locale.ROOT).startsWith("documents/")) {
            normalized = normalized.substring("documents/".length());
        }
        if (normalized.contains("..") || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path");
        }
        // Flat file upload without day folder → stash under today.
        if (!normalized.contains("/")) {
            if (!isAudioFilename(normalized)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an audio recording");
            }
            return LocalDate.now() + "/" + normalized;
        }
        return normalized;
    }

    private static LocalDate parseDayFromRelativePath(String relativePath) {
        String[] parts = relativePath.split("/");
        for (String part : parts) {
            if (DAY_FOLDER.matcher(part).matches()) {
                try {
                    return LocalDate.parse(part);
                } catch (DateTimeParseException ignored) {
                    // continue
                }
            }
        }
        return LocalDate.now();
    }

    private static boolean isAudioFilename(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        // webkitRelativePath may include folders — check the leaf name.
        int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        String leaf = slash >= 0 ? lower.substring(slash + 1) : lower;
        return AUDIO_EXT.stream().anyMatch(leaf::endsWith);
    }

    private static boolean isImageFilename(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        String leaf = slash >= 0 ? lower.substring(slash + 1) : lower;
        return IMAGE_EXT.stream().anyMatch(leaf::endsWith);
    }

    private static String guessContentType(String filename, String uploaded) {
        if (uploaded != null && !uploaded.isBlank() && !"application/octet-stream".equalsIgnoreCase(uploaded)) {
            return uploaded;
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".webm")) {
            return "audio/webm";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        return "audio/mp4";
    }

    private static String guessImageContentType(String filename, String uploaded) {
        if (uploaded != null && !uploaded.isBlank() && !"application/octet-stream".equalsIgnoreCase(uploaded)) {
            return uploaded;
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".heic") || lower.endsWith(".heif")) {
            return "image/heic";
        }
        return "image/jpeg";
    }

    private ManagementRecordingItemDto toItem(ManagementRecordingCache cached) {
        boolean hasTranscript = cached.getTranscript() != null && !cached.getTranscript().isBlank();
        boolean hasSummary = cached.getSummary() != null && !cached.getSummary().isBlank();
        long size = cached.getFileSizeBytes() == null ? 0L : cached.getFileSizeBytes();
        return new ManagementRecordingItemDto(
                cached.getRelativePath(),
                cached.getDisplayName(),
                cached.getRecordedDay(),
                size,
                hasTranscript,
                hasSummary,
                cached.getProcessingStatus(),
                cached.getProcessingError());
    }

    private ManagementRecordingDetailDto toDetail(ManagementRecordingCache cached) {
        long size = cached.getFileSizeBytes() == null ? 0L : cached.getFileSizeBytes();
        List<ManagementRecordingImageDto> images = List.of();
        if (cached.getId() != null) {
            images = imageRepository.findByRecordingIdOrderBySortOrderAscIdAsc(cached.getId()).stream()
                    .map(this::toImageDto)
                    .toList();
        }
        return new ManagementRecordingDetailDto(
                cached.getRelativePath(),
                cached.getDisplayName(),
                cached.getRecordedDay(),
                size,
                cached.getTranscript(),
                cached.getTranscriptSource(),
                cached.getTranscribedAt(),
                cached.getSummary(),
                cached.getSummarizedAt(),
                parseSegments(cached.getTranscriptSegmentsJson()),
                images,
                cached.getProcessingStatus(),
                cached.getProcessingError());
    }

    private ManagementRecordingImageDto toImageDto(ManagementRecordingImage img) {
        return new ManagementRecordingImageDto(
                img.getId(),
                img.getOriginalFilename(),
                img.getContentType(),
                img.getSizeBytes(),
                img.getCreatedAt());
    }

    private String serializeSegments(
            List<RhDailyTrackerOpenAiClient.TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        try {
            List<ManagementRecordingTranscriptSegmentDto> dtos = new ArrayList<>(segments.size());
            for (var s : segments) {
                dtos.add(new ManagementRecordingTranscriptSegmentDto(
                        s.speaker(), s.text(), s.startSeconds(), s.endSeconds()));
            }
            return objectMapper.writeValueAsString(dtos);
        } catch (Exception e) {
            log.warn("Could not serialize transcript segments: {}", e.toString());
            return null;
        }
    }

    private List<ManagementRecordingTranscriptSegmentDto> parseSegments(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ManagementRecordingTranscriptSegmentDto> parsed =
                    objectMapper.readValue(json, new TypeReference<>() {});
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception e) {
            log.warn("Could not parse transcript segments JSON: {}", e.toString());
            return List.of();
        }
    }

    public record RecordingFile(String filename, String contentType, byte[] body) {}
}
