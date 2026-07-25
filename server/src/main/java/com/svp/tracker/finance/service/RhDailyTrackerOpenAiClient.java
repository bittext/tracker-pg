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
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Thin OpenAI Chat Completions + Whisper client for Daily Tracker AI and recordings. */
@Component
@Slf4j
public class RhDailyTrackerOpenAiClient {

    private final RobinhoodRhDailyTrackerProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public RhDailyTrackerOpenAiClient(RobinhoodRhDailyTrackerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String completeJson(String systemPrompt, String userPrompt) {
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
            body.put("max_tokens", ai.maxOutputTokens());
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_object");
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            String url = ai.baseUrl() + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(ai.timeoutMs()))
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
        var ai = props.ai();
        if (!ai.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Daily Tracker AI is not configured. Set TRACKER_FINANCE_RH_DAILY_TRACKER_AI_ENABLED=true and TRACKER_FINANCE_RH_DAILY_TRACKER_AI_API_KEY.");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", ai.model());
            body.put("temperature", 0.5);
            body.put("max_tokens", ai.maxOutputTokens());
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            String url = ai.baseUrl() + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(ai.timeoutMs()))
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
     * Whisper transcription for a local audio file (m4a/mp3/wav). Uses the same API key as chat completions.
     * Whisper accepts files up to 25MB; larger files should be trimmed before calling.
     */
    public String transcribeAudio(Path audioFile) {
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
            if (size > 25L * 1024 * 1024) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Audio file exceeds Whisper's 25MB limit (" + size + " bytes)");
            }
            String boundary = "----TrackerWhisper" + UUID.randomUUID().toString().replace("-", "");
            String filename = audioFile.getFileName().toString();
            String contentType = guessAudioContentType(filename);
            byte[] fileBytes = Files.readAllBytes(audioFile);

            List<byte[]> parts = new ArrayList<>();
            parts.add(("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                            + "whisper-1\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            parts.add(("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"response_format\"\r\n\r\n"
                            + "text\r\n")
                    .getBytes(StandardCharsets.UTF_8));
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

            String url = ai.baseUrl() + "/audio/transcriptions";
            // Whisper can take longer than chat; use at least 3 minutes.
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
            String text = response.body() == null ? "" : response.body().trim();
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Whisper returned an empty transcript");
            }
            return text;
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
