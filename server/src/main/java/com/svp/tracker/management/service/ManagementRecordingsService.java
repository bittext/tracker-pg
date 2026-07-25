package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.service.RhDailyTrackerOpenAiClient;
import com.svp.tracker.management.config.ManagementRecordingsProperties;
import com.svp.tracker.management.domain.ManagementRecordingCache;
import com.svp.tracker.management.dto.ManagementRecordingDayDto;
import com.svp.tracker.management.dto.ManagementRecordingDetailDto;
import com.svp.tracker.management.dto.ManagementRecordingItemDto;
import com.svp.tracker.management.dto.ManagementRecordingListDto;
import com.svp.tracker.management.repository.ManagementRecordingCacheRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
public class ManagementRecordingsService {

    private static final Pattern DAY_FOLDER = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Set<String> AUDIO_EXT = Set.of(".m4a", ".mp3", ".wav", ".webm", ".ogg");

    private final ManagementRecordingsProperties properties;
    private final ManagementRecordingCacheRepository cacheRepository;
    private final CurrentUserService currentUser;
    private final RhDailyTrackerOpenAiClient openAi;

    @Transactional(readOnly = true)
    public ManagementRecordingListDto list(LocalDate dayFilter) {
        if (!properties.configured()) {
            return new ManagementRecordingListDto(
                    false,
                    properties.rootPath(),
                    "Recordings are disabled or root path is not configured (TRACKER_MANAGEMENT_RECORDINGS_*).",
                    List.of(),
                    List.of());
        }
        Path root = resolveRoot();
        if (!Files.isDirectory(root)) {
            return new ManagementRecordingListDto(
                    true,
                    root.toString(),
                    "Root folder is missing or not readable: " + root,
                    List.of(),
                    List.of());
        }

        long owner = currentUser.requireUserId();
        Map<String, ManagementRecordingCache> cacheByPath = cacheRepository
                .findByOwnerUserIdOrderByRecordedDayDescUpdatedAtDesc(owner)
                .stream()
                .collect(Collectors.toMap(ManagementRecordingCache::getRelativePath, c -> c, (a, b) -> a));

        List<DiskRecording> disk = scanDisk(root, dayFilter);
        Map<LocalDate, Integer> dayCounts = new HashMap<>();
        List<ManagementRecordingItemDto> items = new ArrayList<>();
        for (DiskRecording r : disk) {
            dayCounts.merge(r.recordedDay(), 1, Integer::sum);
            ManagementRecordingCache cached = cacheByPath.get(r.relativePath());
            items.add(toItem(r, cached));
        }

        List<ManagementRecordingDayDto> days = dayCounts.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Integer>comparingByKey().reversed())
                .map(e -> new ManagementRecordingDayDto(e.getKey().toString(), e.getValue()))
                .toList();

        items.sort(Comparator.comparing(ManagementRecordingItemDto::recordedDay, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ManagementRecordingItemDto::displayName));

        return new ManagementRecordingListDto(true, root.toString(), null, days, items);
    }

    @Transactional(readOnly = true)
    public ManagementRecordingDetailDto detail(String relativePath) {
        DiskRecording disk = requireDiskRecording(relativePath);
        long owner = currentUser.requireUserId();
        ManagementRecordingCache cached = cacheRepository
                .findByOwnerUserIdAndRelativePath(owner, disk.relativePath())
                .orElse(null);
        return toDetail(disk, cached);
    }

    @Transactional(readOnly = true)
    public RecordingFile readFile(String relativePath) {
        DiskRecording disk = requireDiskRecording(relativePath);
        try {
            byte[] body = Files.readAllBytes(disk.absolutePath());
            return new RecordingFile(disk.displayName(), guessContentType(disk.displayName()), body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public ManagementRecordingDetailDto transcribe(String relativePath, boolean force) {
        DiskRecording disk = requireDiskRecording(relativePath);
        long owner = currentUser.requireUserId();
        ManagementRecordingCache cached = cacheRepository
                .findByOwnerUserIdAndRelativePath(owner, disk.relativePath())
                .orElseGet(() -> newCacheRow(owner, disk));

        if (!force
                && cached.getTranscript() != null
                && !cached.getTranscript().isBlank()) {
            return toDetail(disk, cached);
        }

        String transcript = openAi.transcribeAudio(disk.absolutePath());
        cached.setTranscript(transcript);
        cached.setTranscriptSource("whisper-1");
        cached.setTranscribedAt(Instant.now());
        cached.setDisplayName(disk.displayName());
        cached.setRecordedDay(disk.recordedDay());
        cached.setFileSizeBytes(disk.fileSizeBytes());
        cached = cacheRepository.save(cached);
        return toDetail(disk, cached);
    }

    @Transactional
    public ManagementRecordingDetailDto summarize(String relativePath, boolean force) {
        DiskRecording disk = requireDiskRecording(relativePath);
        long owner = currentUser.requireUserId();
        ManagementRecordingCache cached = cacheRepository
                .findByOwnerUserIdAndRelativePath(owner, disk.relativePath())
                .orElseGet(() -> newCacheRow(owner, disk));

        if (cached.getTranscript() == null || cached.getTranscript().isBlank()) {
            // Auto-transcribe once so summarize is a single user action.
            String transcript = openAi.transcribeAudio(disk.absolutePath());
            cached.setTranscript(transcript);
            cached.setTranscriptSource("whisper-1");
            cached.setTranscribedAt(Instant.now());
        }

        if (!force && cached.getSummary() != null && !cached.getSummary().isBlank()) {
            cached.setDisplayName(disk.displayName());
            cached.setRecordedDay(disk.recordedDay());
            cached.setFileSizeBytes(disk.fileSizeBytes());
            cached = cacheRepository.save(cached);
            return toDetail(disk, cached);
        }

        String system = """
                You summarize personal voice memos from Just Press Record.
                Write a concise summary in plain language: key points, action items, and any dates or names mentioned.
                Use short paragraphs or bullets. Do not invent details that are not in the transcript.
                """;
        String user = "Recording: "
                + disk.displayName()
                + " ("
                + disk.recordedDay()
                + ")\n\nTranscript:\n"
                + cached.getTranscript();
        String summary = openAi.completeText(system, user);
        cached.setSummary(summary);
        cached.setSummarizedAt(Instant.now());
        cached.setDisplayName(disk.displayName());
        cached.setRecordedDay(disk.recordedDay());
        cached.setFileSizeBytes(disk.fileSizeBytes());
        cached = cacheRepository.save(cached);
        return toDetail(disk, cached);
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
        Path root = resolveRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        long owner = currentUser.requireUserId();
        String needle = q.toLowerCase(Locale.ROOT);
        Map<String, ManagementRecordingCache> cacheByPath = cacheRepository
                .findByOwnerUserIdOrderByRecordedDayDescUpdatedAtDesc(owner)
                .stream()
                .collect(Collectors.toMap(ManagementRecordingCache::getRelativePath, c -> c, (a, b) -> a));

        Set<String> matched = new HashSet<>();
        for (DiskRecording r : scanDisk(root, null)) {
            if (r.relativePath().toLowerCase(Locale.ROOT).contains(needle)
                    || r.displayName().toLowerCase(Locale.ROOT).contains(needle)) {
                matched.add(r.relativePath());
            }
        }
        for (ManagementRecordingCache c : cacheRepository.search(owner, q)) {
            matched.add(c.getRelativePath());
        }

        List<ManagementRecordingItemDto> out = new ArrayList<>();
        for (String path : matched) {
            try {
                DiskRecording disk = requireDiskRecording(path);
                out.add(toItem(disk, cacheByPath.get(path)));
            } catch (ResponseStatusException ignored) {
                // Cached path no longer on disk — skip.
            }
        }
        out.sort(Comparator.comparing(ManagementRecordingItemDto::recordedDay, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ManagementRecordingItemDto::displayName));
        return out;
    }

    private ManagementRecordingCache newCacheRow(long owner, DiskRecording disk) {
        ManagementRecordingCache c = new ManagementRecordingCache();
        c.setOwnerUserId(owner);
        c.setRelativePath(disk.relativePath());
        c.setDisplayName(disk.displayName());
        c.setRecordedDay(disk.recordedDay());
        c.setFileSizeBytes(disk.fileSizeBytes());
        return c;
    }

    private DiskRecording requireDiskRecording(String relativePath) {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recordings are not configured");
        }
        Path absolute = resolveUnderRoot(relativePath);
        if (!Files.isRegularFile(absolute)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording not found");
        }
        String name = absolute.getFileName().toString();
        if (!isAudioFilename(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an audio recording");
        }
        LocalDate day = parseDayFromParent(absolute.getParent());
        String rel = toRelativePath(resolveRoot(), absolute);
        long size;
        try {
            size = Files.size(absolute);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DiskRecording(rel, name, day, size, absolute);
    }

    private List<DiskRecording> scanDisk(Path root, LocalDate dayFilter) {
        List<DiskRecording> out = new ArrayList<>();
        try (DirectoryStream<Path> days = Files.newDirectoryStream(root)) {
            for (Path dayDir : days) {
                if (!Files.isDirectory(dayDir)) {
                    continue;
                }
                String folderName = dayDir.getFileName().toString();
                if (!DAY_FOLDER.matcher(folderName).matches()) {
                    continue;
                }
                LocalDate day;
                try {
                    day = LocalDate.parse(folderName);
                } catch (DateTimeParseException e) {
                    continue;
                }
                if (dayFilter != null && !day.equals(dayFilter)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dayDir)) {
                    for (Path file : files) {
                        if (!Files.isRegularFile(file)) {
                            continue;
                        }
                        String name = file.getFileName().toString();
                        if (!isAudioFilename(name)) {
                            continue;
                        }
                        long size = Files.size(file);
                        String rel = toRelativePath(root, file);
                        out.add(new DiskRecording(rel, name, day, size, file));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed scanning recordings root {}: {}", root, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to read recordings folder");
        }
        return out;
    }

    private Path resolveRoot() {
        Path root = Path.of(properties.rootPath()).toAbsolutePath().normalize();
        if (!root.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Recordings rootPath must be absolute");
        }
        return root;
    }

    private Path resolveUnderRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }
        String normalized = relativePath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..") || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path");
        }
        Path root = resolveRoot();
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path escapes recordings root");
        }
        return resolved;
    }

    private static String toRelativePath(Path root, Path absolute) {
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    private static LocalDate parseDayFromParent(Path parent) {
        if (parent == null) {
            return null;
        }
        String name = parent.getFileName().toString();
        try {
            return LocalDate.parse(name);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isAudioFilename(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return AUDIO_EXT.stream().anyMatch(lower::endsWith);
    }

    private static String guessContentType(String filename) {
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

    private static ManagementRecordingItemDto toItem(DiskRecording disk, ManagementRecordingCache cached) {
        boolean hasTranscript = cached != null && cached.getTranscript() != null && !cached.getTranscript().isBlank();
        boolean hasSummary = cached != null && cached.getSummary() != null && !cached.getSummary().isBlank();
        return new ManagementRecordingItemDto(
                disk.relativePath(),
                disk.displayName(),
                disk.recordedDay(),
                disk.fileSizeBytes(),
                hasTranscript,
                hasSummary);
    }

    private static ManagementRecordingDetailDto toDetail(DiskRecording disk, ManagementRecordingCache cached) {
        return new ManagementRecordingDetailDto(
                disk.relativePath(),
                disk.displayName(),
                disk.recordedDay(),
                disk.fileSizeBytes(),
                cached == null ? null : cached.getTranscript(),
                cached == null ? null : cached.getTranscriptSource(),
                cached == null ? null : cached.getTranscribedAt(),
                cached == null ? null : cached.getSummary(),
                cached == null ? null : cached.getSummarizedAt());
    }

    public record RecordingFile(String filename, String contentType, byte[] body) {}

    private record DiskRecording(
            String relativePath, String displayName, LocalDate recordedDay, long fileSizeBytes, Path absolutePath) {}
}
