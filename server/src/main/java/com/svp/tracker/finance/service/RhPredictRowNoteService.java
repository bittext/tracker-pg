package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RhPredictRowNote;
import com.svp.tracker.finance.repository.RhPredictRowNoteRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Brief notes on single rows (a day or a close) of the Predict fresh-start tables. */
@Service
@RequiredArgsConstructor
public class RhPredictRowNoteService {

    static final int MAX_BODY = 140;

    /** {@code <journey>:day:YYYY-MM-DD} or {@code <journey>:close:<id>}. */
    private static final Pattern KEY =
            Pattern.compile("^[a-z0-9-]{1,40}:(day:\\d{4}-\\d{2}-\\d{2}|close:\\d{1,18})$");

    private final CurrentUserService currentUser;
    private final RhPredictRowNoteRepository repository;

    /** Every note the current user has written, by row key. */
    public Map<String, String> load() {
        return notesOf(currentUser.requireUserId());
    }

    /** Saves the note for one row, or removes it when the text is blank. Returns all notes. */
    @Transactional
    public Map<String, String> save(String key, String body) {
        long uid = currentUser.requireUserId();
        String noteKey = requireKey(key);
        String text = clean(body);
        var existing = repository.findByOwnerUserIdAndNoteKey(uid, noteKey);
        if (text.isEmpty()) {
            existing.ifPresent(repository::delete);
            repository.flush();
            return notesOf(uid);
        }
        Instant now = Instant.now();
        RhPredictRowNote row = existing.orElseGet(RhPredictRowNote::new);
        if (row.getId() == null) {
            row.setOwnerUserId(uid);
            row.setNoteKey(noteKey);
            row.setCreatedAt(now);
        }
        row.setBody(text);
        row.setUpdatedAt(now);
        repository.saveAndFlush(row);
        return notesOf(uid);
    }

    static String requireKey(String key) {
        String value = key == null ? "" : key.trim();
        if (!KEY.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That row cannot hold a note.");
        }
        return value;
    }

    /** Trims, collapses line breaks into spaces, and caps the length. */
    static String clean(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() > MAX_BODY ? text.substring(0, MAX_BODY).trim() : text;
    }

    private Map<String, String> notesOf(long uid) {
        Map<String, String> notes = new LinkedHashMap<>();
        repository.findByOwnerUserId(uid).stream()
                .sorted((a, b) -> a.getNoteKey().compareTo(b.getNoteKey()))
                .forEach(row -> notes.put(row.getNoteKey(), row.getBody()));
        return notes;
    }
}
