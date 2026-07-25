package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.finance.service.RhDailyTrackerOpenAiClient;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.config.ManagementRecordingsProperties;
import com.svp.tracker.management.domain.ManagementRecordingCache;
import com.svp.tracker.management.dto.ManagementRecordingDayDto;
import com.svp.tracker.management.dto.ManagementRecordingDetailDto;
import com.svp.tracker.management.dto.ManagementRecordingItemDto;
import com.svp.tracker.management.dto.ManagementRecordingListDto;
import com.svp.tracker.management.repository.ManagementRecordingCacheRepository;
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
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    private final ManagementRecordingsProperties properties;
    private final ManagementRecordingCacheRepository cacheRepository;
    private final CurrentUserService currentUser;
    private final RhDailyTrackerOpenAiClient openAi;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;

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
            row.setTranscribedAt(null);
            row.setSummary(null);
            row.setSummarizedAt(null);
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
            String fn = row.getOriginalFilename() != null ? row.getOriginalFilename() : row.getDisplayName();
            return new RecordingFile(fn, ct, body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public void delete(String relativePath) {
        ManagementRecordingCache row = requireStored(relativePath);
        try {
            if (row.getStorageKey() != null) {
                blobStore.delete(row.getStorageKey());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cacheRepository.delete(row);
    }

    @Transactional
    public ManagementRecordingDetailDto transcribe(String relativePath, boolean force) {
        ManagementRecordingCache row = requireStored(relativePath);
        if (!force && row.getTranscript() != null && !row.getTranscript().isBlank()) {
            return toDetail(row);
        }
        Path tmp = writeTempAudio(row);
        try {
            String transcript = openAi.transcribeAudio(tmp);
            row.setTranscript(transcript);
            row.setTranscriptSource("whisper-1");
            row.setTranscribedAt(Instant.now());
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
                String transcript = openAi.transcribeAudio(tmp);
                row.setTranscript(transcript);
                row.setTranscriptSource("whisper-1");
                row.setTranscribedAt(Instant.now());
            } finally {
                deleteQuietly(tmp);
            }
        }
        if (!force && row.getSummary() != null && !row.getSummary().isBlank()) {
            row = cacheRepository.save(row);
            return toDetail(row);
        }
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
        String summary = openAi.completeText(system, user);
        row.setSummary(summary);
        row.setSummarizedAt(Instant.now());
        row = cacheRepository.save(row);
        return toDetail(row);
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
                hasSummary);
    }

    private static ManagementRecordingDetailDto toDetail(ManagementRecordingCache cached) {
        long size = cached.getFileSizeBytes() == null ? 0L : cached.getFileSizeBytes();
        return new ManagementRecordingDetailDto(
                cached.getRelativePath(),
                cached.getDisplayName(),
                cached.getRecordedDay(),
                size,
                cached.getTranscript(),
                cached.getTranscriptSource(),
                cached.getTranscribedAt(),
                cached.getSummary(),
                cached.getSummarizedAt());
    }

    public record RecordingFile(String filename, String contentType, byte[] body) {}
}
