package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RhPredictClose;
import com.svp.tracker.finance.domain.RhPredictNote;
import com.svp.tracker.finance.domain.RhPredictNoteFeedback;
import com.svp.tracker.finance.dto.RhPredictFeedbackDto;
import com.svp.tracker.finance.dto.RhPredictNoteDto;
import com.svp.tracker.finance.dto.RhPredictNotesDeskDto;
import com.svp.tracker.finance.repository.RhPredictCloseRepository;
import com.svp.tracker.finance.repository.RhPredictNoteFeedbackRepository;
import com.svp.tracker.finance.repository.RhPredictNoteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RhPredictNoteService {

    private static final int MAX_BODY = 4000;
    private static final int MAX_NOTES = 40;
    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US).withZone(CENTRAL);

    private static final String SYSTEM_PROMPT = """
            You review a person's own notes about Robinhood Predict event contracts.
            Reply with JSON only, in this shape:
            {"headline":"one sentence","points":["3 to 5 short feedback sentences"],"watch":"one thing to check next"}
            Talk about the notes and the closed-book summary that is supplied.
            Say when a claimed pattern is supported by that summary, and when the notes are too thin to tell.
            Do not tell them to buy, sell, or change a position size.
            Use plain language.
            """;

    private final CurrentUserService currentUser;
    private final RhPredictNoteRepository noteRepository;
    private final RhPredictNoteFeedbackRepository feedbackRepository;
    private final RhPredictCloseRepository closeRepository;
    private final RhDailyTrackerOpenAiClient openAi;
    private final RobinhoodRhDailyTrackerProperties props;
    private final ObjectMapper objectMapper;

    public RhPredictNotesDeskDto load() {
        long uid = currentUser.requireUserId();
        return desk(uid);
    }

    @Transactional
    public RhPredictNotesDeskDto add(String body) {
        long uid = currentUser.requireUserId();
        String text = clean(body);
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a note before saving.");
        }
        if (noteRepository.countByOwnerUserId(uid) >= MAX_NOTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Remove an older note before adding another.");
        }
        Instant now = Instant.now();
        RhPredictNote row = new RhPredictNote();
        row.setOwnerUserId(uid);
        row.setBody(text);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        noteRepository.save(row);
        return desk(uid);
    }

    @Transactional
    public RhPredictNotesDeskDto delete(long id) {
        long uid = currentUser.requireUserId();
        RhPredictNote row = noteRepository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        noteRepository.delete(row);
        return desk(uid);
    }

    @Transactional
    public RhPredictNotesDeskDto feedback() {
        long uid = currentUser.requireUserId();
        List<RhPredictNote> notes = noteRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(uid);
        if (notes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save a note before asking for feedback.");
        }
        String completion = openAi.completeJson(SYSTEM_PROMPT, prompt(notes, closeRepository.findByOwnerUserIdOrderByClosedAtDescIdDesc(uid)));
        Parsed parsed = parse(completion);
        Instant now = Instant.now();
        RhPredictNoteFeedback row = feedbackRepository.findByOwnerUserId(uid).orElseGet(RhPredictNoteFeedback::new);
        if (row.getId() == null) {
            row.setOwnerUserId(uid);
            row.setCreatedAt(now);
        }
        row.setHeadline(clip(parsed.headline(), 500));
        row.setWatch(clip(parsed.watch(), 500));
        row.setModel(props.ai().model() == null ? "" : props.ai().model());
        row.setUpdatedAt(now);
        try {
            row.setPointsJson(objectMapper.writeValueAsString(parsed.points()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the feedback.");
        }
        feedbackRepository.save(row);
        return desk(uid);
    }

    private RhPredictNotesDeskDto desk(long uid) {
        List<RhPredictNoteDto> notes = noteRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(uid).stream()
                .map(row -> new RhPredictNoteDto(row.getId(), row.getBody(), row.getCreatedAt()))
                .toList();
        return new RhPredictNotesDeskDto(notes, feedbackOf(uid));
    }

    private RhPredictFeedbackDto feedbackOf(long uid) {
        return feedbackRepository.findByOwnerUserId(uid).map(this::toFeedback).orElse(null);
    }

    private RhPredictFeedbackDto toFeedback(RhPredictNoteFeedback row) {
        List<String> points = List.of();
        try {
            JsonNode node = objectMapper.readTree(row.getPointsJson() == null ? "[]" : row.getPointsJson());
            if (node.isArray()) {
                List<String> parsed = new ArrayList<>();
                for (JsonNode item : node) {
                    String text = item.asText("").trim();
                    if (!text.isEmpty()) {
                        parsed.add(text);
                    }
                }
                points = List.copyOf(parsed);
            }
        } catch (Exception ignored) {
            points = List.of();
        }
        return new RhPredictFeedbackDto(row.getHeadline(), points, row.getWatch(), row.getUpdatedAt());
    }

    private String prompt(List<RhPredictNote> notes, List<RhPredictClose> closes) {
        StringBuilder text = new StringBuilder();
        text.append("Notes, newest first:\n");
        int shown = 0;
        for (RhPredictNote note : notes) {
            if (shown++ == 20) {
                break;
            }
            text.append("- ")
                    .append(WHEN.format(note.getCreatedAt()))
                    .append(": ")
                    .append(note.getBody().replace('\n', ' '))
                    .append('\n');
        }
        text.append("\nClosed book:\n");
        int wins = 0;
        int losses = 0;
        int flat = 0;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        for (RhPredictClose close : closes) {
            BigDecimal payback = close.getQuantity().multiply(close.getPrice());
            BigDecimal stake = payback.subtract(close.getRealized());
            invested = invested.add(stake);
            returned = returned.add(close.getRealized());
            int sign = close.getRealized().signum();
            if (sign > 0) {
                wins++;
            } else if (sign < 0) {
                losses++;
            } else {
                flat++;
            }
        }
        text.append(closes.size())
                .append(" closes, ")
                .append(wins)
                .append(" up, ")
                .append(losses)
                .append(" down, ")
                .append(flat)
                .append(" flat. Cash put in ")
                .append(money(invested))
                .append(", net ")
                .append(money(returned))
                .append(".\nRecent closes:\n");
        int recent = 0;
        for (RhPredictClose close : closes) {
            if (recent++ == 12) {
                break;
            }
            BigDecimal payback = close.getQuantity().multiply(close.getPrice());
            BigDecimal stake = payback.subtract(close.getRealized());
            text.append("- ")
                    .append(WHEN.format(close.getClosedAt()))
                    .append(" exit ")
                    .append(money(close.getPrice()))
                    .append(" invested ")
                    .append(money(stake))
                    .append(" return ")
                    .append(money(close.getRealized()))
                    .append('\n');
        }
        return text.toString();
    }

    private Parsed parse(String completion) {
        try {
            JsonNode root = objectMapper.readTree(completion);
            String headline = root.path("headline").asText("").trim();
            String watch = root.path("watch").asText("").trim();
            List<String> points = new ArrayList<>();
            JsonNode array = root.path("points");
            if (array.isArray()) {
                for (JsonNode item : array) {
                    String point = item.asText("").trim();
                    if (!point.isEmpty()) {
                        points.add(point);
                    }
                    if (points.size() == 6) {
                        break;
                    }
                }
            }
            if (headline.isEmpty() && points.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The feedback came back empty.");
            }
            return new Parsed(headline, List.copyOf(points), watch);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read the feedback.");
        }
    }

    private static String clean(String body) {
        if (body == null) {
            return "";
        }
        String text = body.trim();
        if (text.length() > MAX_BODY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep a note under 4,000 characters.");
        }
        return text;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String money(BigDecimal value) {
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record Parsed(String headline, List<String> points, String watch) {}
}
