package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Thin OpenAI Chat Completions + Whisper client for Daily Tracker AI and recordings. */
@Component
@Slf4j
public class RhDailyTrackerOpenAiClient {

    /** Headroom under Whisper's hard 25MB API limit. */
    private static final long WHISPER_MAX_BYTES = 24L * 1024 * 1024;
    private static final String FFMPEG = "ffmpeg";
    private static final String FFPROBE = "ffprobe";

    private final RobinhoodRhDailyTrackerProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public RhDailyTrackerOpenAiClient(RobinhoodRhDailyTrackerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String completeJson(String systemPrompt, String userPrompt) {
        return completeJson(systemPrompt, userPrompt, props.ai().maxOutputTokens());
    }

    public String completeJson(String systemPrompt, String userPrompt, int maxTokens) {
        var ai = props.ai();
        if (!ai.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Daily Tracker AI is not configured. Set TRACKER_FINANCE_RH_DAILY_TRACKER_AI_ENABLED=true and TRACKER_FINANCE_RH_DAILY_TRACKER_AI_API_KEY.");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", ai.model());
            body.put("temperature", 0.4);
            body.put("max_tokens", Math.max(256, maxTokens));
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_object");
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            String url = ai.baseUrl() + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(ai.timeoutMs(), 120_000L)))
                    .header("Authorization", "Bearer " + ai.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn(
                        "OpenAI chat completions failed status={} bodyLen={}",
                        response.statusCode(),
                        response.body() == null ? 0 : response.body().length());
                throw new ResponseStatusException(
                        mapOpenAiHttpStatus(response.statusCode()),
                        openAiFailureMessage(response.statusCode(), response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText("").isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned an empty completion");
            }
            return content.asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request interrupted");
        } catch (Exception e) {
            log.warn("OpenAI chat completions error: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed: " + e.getMessage());
        }
    }

    /** Plain-text completion (no JSON response_format). */
    public String completeText(String systemPrompt, String userPrompt) {
        return completeText(systemPrompt, userPrompt, props.ai().maxOutputTokens(), 0.5);
    }

    public String completeText(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        var ai = props.ai();
        if (!ai.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Daily Tracker AI is not configured. Set TRACKER_FINANCE_RH_DAILY_TRACKER_AI_ENABLED=true and TRACKER_FINANCE_RH_DAILY_TRACKER_AI_API_KEY.");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", ai.model());
            body.put("temperature", temperature);
            body.put("max_tokens", Math.max(256, maxTokens));
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            String url = ai.baseUrl() + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(ai.timeoutMs(), 120_000L)))
                    .header("Authorization", "Bearer " + ai.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        mapOpenAiHttpStatus(response.statusCode()),
                        openAiFailureMessage(response.statusCode(), response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText("").isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned an empty completion");
            }
            return content.asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request interrupted");
        } catch (Exception e) {
            log.warn("OpenAI text completion error: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed: " + e.getMessage());
        }
    }

    /**
     * Transcribe a local audio file (m4a/mp3/wav). Prefers speaker-aware diarization; otherwise Whisper + a chat
     * pass that splits the text into one paragraph per speaker turn.
     * Files over Whisper's ~25MB limit are compressed (and split if needed) with ffmpeg first.
     */
    public TranscriptionResult transcribeAudio(Path audioFile) {
        var ai = props.ai();
        if (!ai.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Daily Tracker AI is not configured. Set TRACKER_FINANCE_RH_DAILY_TRACKER_AI_ENABLED=true and TRACKER_FINANCE_RH_DAILY_TRACKER_AI_API_KEY.");
        }
        if (audioFile == null || !Files.isRegularFile(audioFile)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is missing");
        }
        try {
            long size = Files.size(audioFile);
            if (size <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is empty");
            }
            if (size <= WHISPER_MAX_BYTES) {
                return transcribeSingleFile(audioFile);
            }
            log.info(
                    "Audio {} bytes exceeds Whisper limit; compressing/splitting with ffmpeg before transcription",
                    size);
            return transcribeOversizedFile(audioFile, size);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Whisper request interrupted");
        } catch (Exception e) {
            log.warn("OpenAI Whisper error: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Whisper request failed: " + e.getMessage());
        }
    }

    private TranscriptionResult transcribeOversizedFile(Path audioFile, long originalSize) throws Exception {
        Path workDir = Files.createTempDirectory("tracker-whisper-");
        try {
            Path compressed = compressForWhisper(audioFile, workDir);
            long compressedSize = Files.size(compressed);
            log.info(
                    "Compressed recording for Whisper: {} → {} bytes",
                    originalSize,
                    compressedSize);
            if (compressedSize <= WHISPER_MAX_BYTES) {
                TranscriptionResult result = transcribeSingleFile(compressed);
                return new TranscriptionResult(
                        result.text(), result.source() + "+compressed", result.segments());
            }
            List<Path> chunks = splitForWhisper(compressed, workDir);
            if (chunks.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "ffmpeg produced no audio chunks for oversized recording");
            }
            log.info("Split oversized recording into {} Whisper chunks", chunks.size());
            return transcribeChunks(chunks);
        } finally {
            deleteTreeQuietly(workDir);
        }
    }

    private TranscriptionResult transcribeChunks(List<Path> chunks) throws Exception {
        StringBuilder text = new StringBuilder();
        List<TranscriptSegment> segments = new ArrayList<>();
        String source = null;
        double offsetSeconds = 0.0;
        for (int i = 0; i < chunks.size(); i++) {
            Path chunk = chunks.get(i);
            TranscriptionResult part = transcribeSingleFile(chunk);
            if (source == null) {
                source = part.source();
            }
            if (!part.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append("\n\n");
                }
                text.append(part.text().trim());
            }
            for (TranscriptSegment seg : part.segments()) {
                segments.add(new TranscriptSegment(
                        seg.speaker(),
                        seg.text(),
                        shiftSeconds(seg.startSeconds(), offsetSeconds),
                        shiftSeconds(seg.endSeconds(), offsetSeconds)));
            }
            offsetSeconds += probeDurationSeconds(chunk);
        }
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Whisper returned an empty transcript");
        }
        String mergedSource = (source == null ? "whisper-1" : source) + "+chunked";
        String mergedText = text.toString().trim();
        if (!hasSpeakerLabels(mergedText) && !segments.isEmpty()) {
            // Prefer timed segments from chunked Whisper when chat speaker labels are absent.
            return new TranscriptionResult(mergedText, mergedSource, segments);
        }
        if (!hasSpeakerLabels(mergedText)) {
            try {
                String split = splitTranscriptBySpeaker(mergedText);
                if (hasSpeakerLabels(split)) {
                    return new TranscriptionResult(split, mergedSource + "+speakers", List.of());
                }
            } catch (ResponseStatusException splitError) {
                log.warn("Chat speaker split failed after chunked transcription ({})", splitError.getReason());
            }
        }
        return new TranscriptionResult(mergedText, mergedSource, segments);
    }

    private static Double shiftSeconds(Double value, double offsetSeconds) {
        if (value == null) {
            return null;
        }
        return value + offsetSeconds;
    }

    /** Core transcription for a single file already under Whisper's size limit. */
    private TranscriptionResult transcribeSingleFile(Path audioFile) throws Exception {
        String text = null;
        String source = null;
        List<TranscriptSegment> segments = List.of();

        try {
            String diarized = requestTranscription(
                    audioFile,
                    "gpt-4o-transcribe-diarize",
                    "diarized_json",
                    true);
            DiarizedTranscript parsed = parseDiarizedTranscript(diarized);
            if (hasSpeakerLabels(parsed.text())) {
                return new TranscriptionResult(
                        parsed.text(), "gpt-4o-transcribe-diarize", parsed.segments());
            }
            if (!parsed.text().isBlank()) {
                // Model returned flat text without speaker segments — still keep the words/times.
                text = parsed.text();
                source = "gpt-4o-transcribe-diarize";
                segments = parsed.segments();
                log.warn("Diarize returned text without speaker segments; applying chat speaker split");
            }
        } catch (ResponseStatusException diarizeError) {
            log.warn(
                    "Speaker diarization unavailable ({}), falling back to whisper-1 + chat speaker split",
                    diarizeError.getReason());
        }

        if (text == null || text.isBlank()) {
            WhisperVerbose verbose = requestWhisperVerbose(audioFile);
            if (verbose.text().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Whisper returned an empty transcript");
            }
            text = verbose.text();
            source = "whisper-1";
            segments = verbose.segments();
        }

        if (!hasSpeakerLabels(text)) {
            try {
                String split = splitTranscriptBySpeaker(text);
                if (hasSpeakerLabels(split)) {
                    // Chat speaker turns are not time-aligned — drop segment times.
                    return new TranscriptionResult(split, source + "+speakers", List.of());
                }
                log.warn("Chat speaker split did not produce labeled turns; returning flat transcript");
            } catch (ResponseStatusException splitError) {
                log.warn("Chat speaker split failed ({}), returning flat transcript", splitError.getReason());
            }
        }
        return new TranscriptionResult(text, source, segments == null ? List.of() : segments);
    }

    private Path compressForWhisper(Path input, Path workDir) throws Exception {
        requireFfmpeg();
        Path out = workDir.resolve("compressed.mp3");
        // 16 kHz mono is Whisper-friendly and typically brings long Just Press Record clips under 25MB.
        runProcess(
                List.of(
                        FFMPEG,
                        "-y",
                        "-i",
                        input.toAbsolutePath().toString(),
                        "-vn",
                        "-ac",
                        "1",
                        "-ar",
                        "16000",
                        "-c:a",
                        "libmp3lame",
                        "-b:a",
                        "48k",
                        out.toAbsolutePath().toString()),
                10,
                "ffmpeg compress");
        if (!Files.isRegularFile(out) || Files.size(out) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ffmpeg compression produced an empty file");
        }
        return out;
    }

    private List<Path> splitForWhisper(Path input, Path workDir) throws Exception {
        requireFfmpeg();
        double duration = probeDurationSeconds(input);
        long size = Files.size(input);
        if (duration <= 0 || !Double.isFinite(duration)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read audio duration for chunking");
        }
        double chunkSeconds = Math.max(60.0, duration * (WHISPER_MAX_BYTES * 0.85d) / Math.max(1L, size));
        // Avoid a useless single "chunk" that remains oversized.
        if (chunkSeconds >= duration) {
            chunkSeconds = Math.max(60.0, duration / 2.0);
        }
        Path pattern = workDir.resolve("chunk_%03d.mp3");
        runProcess(
                List.of(
                        FFMPEG,
                        "-y",
                        "-i",
                        input.toAbsolutePath().toString(),
                        "-f",
                        "segment",
                        "-segment_time",
                        String.format(Locale.ROOT, "%.1f", chunkSeconds),
                        "-reset_timestamps",
                        "1",
                        "-ac",
                        "1",
                        "-ar",
                        "16000",
                        "-c:a",
                        "libmp3lame",
                        "-b:a",
                        "48k",
                        pattern.toAbsolutePath().toString()),
                20,
                "ffmpeg split");
        try (Stream<Path> stream = Files.list(workDir)) {
            List<Path> chunks = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("chunk_") && name.endsWith(".mp3");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path chunk : chunks) {
                if (Files.size(chunk) > WHISPER_MAX_BYTES) {
                    throw new ResponseStatusException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "Audio chunk still exceeds Whisper's 25MB limit after splitting ("
                                    + Files.size(chunk)
                                    + " bytes). Try a shorter recording.");
                }
            }
            return chunks;
        }
    }

    private double probeDurationSeconds(Path audioFile) throws Exception {
        requireFfmpeg();
        String out = runProcessCapture(
                List.of(
                        FFPROBE,
                        "-v",
                        "error",
                        "-show_entries",
                        "format=duration",
                        "-of",
                        "default=noprint_wrappers=1:nokey=1",
                        audioFile.toAbsolutePath().toString()),
                2,
                "ffprobe duration");
        try {
            return Double.parseDouble(out.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void requireFfmpeg() {
        if (!commandExists(FFMPEG) || !commandExists(FFPROBE)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ffmpeg is required to transcribe recordings larger than 25MB. Install ffmpeg or redeploy the API image.");
        }
    }

    private static boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runProcess(List<String> command, int timeoutMinutes, String label) throws Exception {
        runProcessCapture(command, timeoutMinutes, label);
    }

    private String runProcessCapture(List<String> command, int timeoutMinutes, String label) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, label + " timed out");
        }
        if (process.exitValue() != 0) {
            String hint = output == null ? "" : output.trim();
            if (hint.length() > 240) {
                hint = hint.substring(hint.length() - 240);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, label + " failed (exit " + process.exitValue() + "): " + hint);
        }
        return output == null ? "" : output;
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /**
     * Rewrite a flat transcript into speaker paragraphs using the configured chat model.
     * Used when native diarization is unavailable (common for some API keys / regions).
     */
    private String splitTranscriptBySpeaker(String flatTranscript) {
        String system =
                """
                You rewrite voice recordings into speaker turns for a transcript UI.

                Rules:
                - Infer when the speaker changes from conversational cues (questions vs answers, \
                greetings, names, contrasting viewpoints, or clear hand-offs).
                - Label speakers as Speaker A, Speaker B, Speaker C, … in order of first appearance.
                - If only one person is speaking (a solo memo), use a single Speaker A block.
                - Keep the wording faithful. Do not invent facts, names, or dialogue that are not in the input.
                - Do not add commentary, titles, or markdown — only speaker blocks.
                - Put a blank line between turns. Exact format:

                Speaker A:
                Their words here.

                Speaker B:
                Their reply here.
                """;
        // Long transcripts need headroom beyond the Daily Tracker default (often ~1200).
        int maxTokens = Math.max(props.ai().maxOutputTokens(), 4_000);
        return completeText(system, "Transcript:\n\n" + flatTranscript, maxTokens, 0.2).trim();
    }

    private static boolean hasSpeakerLabels(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // At least one labeled turn, preferably with a paragraph break pattern.
        return text.matches("(?s)(?i).*\\bSpeaker\\s+[A-Z0-9][^:\\n]{0,40}:\\s*.+.*");
    }

    private String requestTranscription(
            Path audioFile, String model, String responseFormat, boolean withChunking)
            throws Exception {
        String boundary = "----TrackerWhisper" + UUID.randomUUID().toString().replace("-", "");
        String filename = audioFile.getFileName().toString();
        String contentType = guessAudioContentType(filename);
        byte[] fileBytes = Files.readAllBytes(audioFile);

        List<byte[]> parts = new ArrayList<>();
        parts.add(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                        + model
                        + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        parts.add(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"response_format\"\r\n\r\n"
                        + responseFormat
                        + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        if (withChunking) {
            // Required for gpt-4o-transcribe-diarize when audio is longer than ~30s.
            parts.add(("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"chunking_strategy\"\r\n\r\n"
                            + "auto\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        parts.add(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\""
                        + filename.replace("\"", "")
                        + "\"\r\n"
                        + "Content-Type: "
                        + contentType
                        + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        parts.add(fileBytes);
        parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] body = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, body, off, p.length);
            off += p.length;
        }

        var ai = props.ai();
        String url = ai.baseUrl() + "/audio/transcriptions";
        long timeoutMs = Math.max(ai.timeoutMs(), 180_000L);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + ai.apiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(
                    mapOpenAiHttpStatus(response.statusCode()),
                    openAiFailureMessage(response.statusCode(), response.body()));
        }
        return response.body() == null ? "" : response.body();
    }

    /**
     * Convert diarized_json segments into one paragraph per speaker turn (with start/end seconds):
     *
     * <pre>
     * Speaker A:
     * Hello there.
     *
     * Speaker B:
     * Hi — how are you?
     * </pre>
     */
    private DiarizedTranscript parseDiarizedTranscript(String rawJson) throws Exception {
        if (rawJson == null || rawJson.isBlank()) {
            return new DiarizedTranscript("", List.of());
        }
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode segments = root.path("segments");
        if (!segments.isArray() || segments.isEmpty()) {
            // Some responses nest segments under "results" or only return flat text.
            segments = root.path("results");
        }
        if ((!segments.isArray() || segments.isEmpty()) && root.path("utterances").isArray()) {
            segments = root.path("utterances");
        }
        if (!segments.isArray() || segments.isEmpty()) {
            String flat = root.path("text").asText("").trim();
            return new DiarizedTranscript(flat, List.of());
        }

        StringBuilder out = new StringBuilder();
        List<TranscriptSegment> turns = new ArrayList<>();
        String currentSpeaker = null;
        StringBuilder turn = new StringBuilder();
        Double turnStart = null;
        Double turnEnd = null;

        for (JsonNode seg : segments) {
            String speaker = firstNonBlank(
                    jsonText(seg.get("speaker")),
                    jsonText(seg.get("speaker_label")),
                    jsonText(seg.get("speaker_id")));
            if (speaker.isBlank()) {
                speaker = "Speaker";
            }
            // Normalize A/B/0/1 → display labels.
            String label = normalizeSpeakerLabel(speaker);
            String segmentText =
                    firstNonBlank(jsonText(seg.get("text")), jsonText(seg.get("transcript")));
            if (segmentText.isBlank()) {
                continue;
            }
            Double segStart = jsonSeconds(seg.get("start"));
            Double segEnd = jsonSeconds(seg.get("end"));
            if (currentSpeaker == null) {
                currentSpeaker = label;
                turn.append(segmentText);
                turnStart = segStart;
                turnEnd = segEnd;
            } else if (currentSpeaker.equals(label)) {
                if (!turn.isEmpty() && !Character.isWhitespace(turn.charAt(turn.length() - 1))) {
                    turn.append(' ');
                }
                turn.append(segmentText);
                if (segEnd != null) {
                    turnEnd = segEnd;
                }
            } else {
                appendSpeakerParagraph(out, currentSpeaker, turn.toString());
                turns.add(new TranscriptSegment(currentSpeaker, turn.toString().trim(), turnStart, turnEnd));
                currentSpeaker = label;
                turn.setLength(0);
                turn.append(segmentText);
                turnStart = segStart;
                turnEnd = segEnd;
            }
        }
        if (currentSpeaker != null && !turn.isEmpty()) {
            appendSpeakerParagraph(out, currentSpeaker, turn.toString());
            turns.add(new TranscriptSegment(currentSpeaker, turn.toString().trim(), turnStart, turnEnd));
        }
        return new DiarizedTranscript(out.toString().trim(), List.copyOf(turns));
    }

    /** Whisper verbose_json: full text plus timed segments (no speakers). */
    private WhisperVerbose requestWhisperVerbose(Path audioFile) throws Exception {
        String raw = requestTranscription(audioFile, "whisper-1", "verbose_json", false);
        if (raw == null || raw.isBlank()) {
            return new WhisperVerbose("", List.of());
        }
        // Older clients sometimes got plain text back if the API ignored verbose_json.
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return new WhisperVerbose(trimmed, List.of());
        }
        JsonNode root = objectMapper.readTree(trimmed);
        String text = root.path("text").asText("").trim();
        JsonNode segs = root.path("segments");
        List<TranscriptSegment> out = new ArrayList<>();
        if (segs.isArray()) {
            for (JsonNode seg : segs) {
                String segmentText = jsonText(seg.get("text"));
                if (segmentText.isBlank()) {
                    continue;
                }
                out.add(new TranscriptSegment(
                        null, segmentText.trim(), jsonSeconds(seg.get("start")), jsonSeconds(seg.get("end"))));
            }
        }
        if (text.isBlank() && !out.isEmpty()) {
            StringBuilder joined = new StringBuilder();
            for (TranscriptSegment s : out) {
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(s.text());
            }
            text = joined.toString().trim();
        }
        return new WhisperVerbose(text, List.copyOf(out));
    }

    private static Double jsonSeconds(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            double v = n.asDouble();
            return Double.isFinite(v) && v >= 0 ? v : null;
        }
        if (n.isTextual()) {
            try {
                double v = Double.parseDouble(n.asText("").trim());
                return Double.isFinite(v) && v >= 0 ? v : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String jsonText(JsonNode n) {
        if (n == null || n.isNull()) {
            return "";
        }
        if (n.isNumber()) {
            return n.asText();
        }
        if (!n.isTextual()) {
            return "";
        }
        return n.asText("").trim();
    }

    private static void appendSpeakerParagraph(StringBuilder out, String speaker, String text) {
        if (!out.isEmpty()) {
            out.append("\n\n");
        }
        out.append(speaker).append(":\n").append(text.trim());
    }

    /** Map API labels like "A", "SPEAKER_00", "speaker_1" to "Speaker A" / "Speaker 1". */
    private static String normalizeSpeakerLabel(String raw) {
        String s = raw.trim();
        if (s.regionMatches(true, 0, "Speaker", 0, 7)) {
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
        // SPEAKER_00 / speaker_1
        if (s.matches("(?i)speaker[_\\- ]?\\d+")) {
            String digits = s.replaceAll("\\D+", "");
            return "Speaker " + digits;
        }
        if (s.length() == 1 && Character.isLetter(s.charAt(0))) {
            return "Speaker " + Character.toUpperCase(s.charAt(0));
        }
        if (s.matches("\\d+")) {
            return "Speaker " + s;
        }
        return "Speaker " + s;
    }

    public record TranscriptionResult(String text, String source, List<TranscriptSegment> segments) {
        public TranscriptionResult {
            segments = segments == null ? List.of() : List.copyOf(segments);
        }
    }

    public record TranscriptSegment(String speaker, String text, Double startSeconds, Double endSeconds) {}

    private record DiarizedTranscript(String text, List<TranscriptSegment> segments) {}

    private record WhisperVerbose(String text, List<TranscriptSegment> segments) {}

    private static String guessAudioContentType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
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

    private static HttpStatus mapOpenAiHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return HttpStatus.BAD_REQUEST;
        }
        if (status == 429) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (status >= 500) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private static String openAiFailureMessage(int status, String body) {
        if (status == 429) {
            return "OpenAI rate limit (HTTP 429). Wait a minute and retry, or check usage/billing at platform.openai.com.";
        }
        if (status == 401 || status == 403) {
            return "OpenAI rejected the API key (HTTP " + status + "). Check TRACKER_FINANCE_RH_DAILY_TRACKER_AI_API_KEY.";
        }
        String hint = "";
        if (body != null && !body.isBlank()) {
            String trimmed = body.trim();
            if (trimmed.length() > 180) {
                trimmed = trimmed.substring(0, 180) + "…";
            }
            hint = " — " + trimmed;
        }
        return "OpenAI request failed (HTTP " + status + ")" + hint;
    }
}
