package com.svp.tracker.life.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.life.domain.LifeMonthNote;
import com.svp.tracker.life.domain.LifeMonthNoteAttachment;
import com.svp.tracker.life.dto.LifeMonthNoteAttachmentDto;
import com.svp.tracker.life.dto.LifeMonthNoteCalendarDto;
import com.svp.tracker.life.dto.LifeMonthNoteDto;
import com.svp.tracker.life.dto.LifeMonthNoteWriteRequest;
import com.svp.tracker.life.repository.LifeMonthNoteAttachmentRepository;
import com.svp.tracker.life.repository.LifeMonthNoteRepository;
import com.svp.tracker.util.HeicImageNormalizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LifeMonthNoteService {

    private final LifeMonthNoteRepository noteRepository;
    private final LifeMonthNoteAttachmentRepository attachmentRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public LifeMonthNoteCalendarDto calendar(int year) {
        if (year < 1970 || year > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
        long owner = currentUser.requireUserId();
        List<Object[]> rows = noteRepository.countByMonthForYear(owner, year);
        Map<Integer, Long> byMonth = new HashMap<>();
        for (Object[] r : rows) {
            byMonth.put((Integer) r[0], (Long) r[1]);
        }
        List<LifeMonthNoteCalendarDto.MonthCount> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            months.add(new LifeMonthNoteCalendarDto.MonthCount(m, byMonth.getOrDefault(m, 0L)));
        }
        return new LifeMonthNoteCalendarDto(year, months);
    }

    @Transactional(readOnly = true)
    public List<LifeMonthNoteDto> list(int year, Integer month) {
        if (year < 1970 || year > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
        long owner = currentUser.requireUserId();
        List<LifeMonthNote> list;
        if (month == null) {
            list = noteRepository.findByOwnerAndYearWithAttachments(owner, year);
        } else {
            if (month < 1 || month > 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month");
            }
            list = noteRepository.findByOwnerAndYearMonthWithAttachments(owner, year, month);
        }
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LifeMonthNoteDto get(long id) {
        LifeMonthNote n = noteRepository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Life note not found: " + id));
        assertRowAccess(n.getOwnerUserId());
        return toDto(n);
    }

    @Transactional
    public LifeMonthNoteDto create(LifeMonthNoteWriteRequest req) {
        long owner = currentUser.requireUserId();
        Instant now = Instant.now();
        LifeMonthNote n = new LifeMonthNote();
        n.setOwnerUserId(owner);
        n.setYear(req.year());
        n.setMonth(req.month());
        n.setSubject(req.subject().trim());
        n.setBody(req.body() == null ? "" : req.body());
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        n = noteRepository.save(n);
        return toDto(noteRepository.findByIdWithAttachments(n.getId()).orElse(n));
    }

    @Transactional
    public LifeMonthNoteDto update(long id, LifeMonthNoteWriteRequest req) {
        LifeMonthNote n = noteRepository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Life note not found: " + id));
        assertRowAccess(n.getOwnerUserId());
        n.setYear(req.year());
        n.setMonth(req.month());
        n.setSubject(req.subject().trim());
        n.setBody(req.body() == null ? "" : req.body());
        n.setUpdatedAt(Instant.now());
        n = noteRepository.save(n);
        return toDto(noteRepository.findByIdWithAttachments(n.getId()).orElse(n));
    }

    @Transactional
    public void delete(long id) {
        LifeMonthNote n = noteRepository
                .findByIdWithAttachments(id)
                .orElseThrow(() -> new NotFoundException("Life note not found: " + id));
        assertRowAccess(n.getOwnerUserId());
        for (LifeMonthNoteAttachment a : new ArrayList<>(n.getAttachments())) {
            try {
                blobStore.delete(a.getStorageKey());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        noteRepository.deleteById(id);
    }

    @Transactional
    public LifeMonthNoteAttachmentDto addAttachment(long noteId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        LifeMonthNote n = noteRepository
                .findByIdWithAttachments(noteId)
                .orElseThrow(() -> new NotFoundException("Life note not found: " + noteId));
        assertRowAccess(n.getOwnerUserId());
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        var normalized = HeicImageNormalizer.normalize(filename, file.getContentType(), bytes);
        String key;
        try (var in = new ByteArrayInputStream(normalized.bytes())) {
            key = blobStore.put(n.getOwnerUserId(), n.getId(), in, normalized.bytes().length);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        LifeMonthNoteAttachment a = new LifeMonthNoteAttachment();
        a.setNote(n);
        a.setStorageKey(key);
        a.setOriginalFilename(normalized.filename());
        a.setContentType(normalized.contentType());
        a.setSizeBytes((long) normalized.bytes().length);
        a.setCreatedAt(Instant.now());
        a = attachmentRepository.save(a);
        n.setUpdatedAt(Instant.now());
        return toAttachmentDto(a);
    }

    @Transactional
    public void deleteAttachment(long attachmentId) {
        LifeMonthNoteAttachment a = attachmentRepository
                .findByIdWithNote(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getNote().getOwnerUserId());
        try {
            blobStore.delete(a.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LifeMonthNote n = a.getNote();
        n.getAttachments().remove(a);
        n.setBody(stripAttachmentEmbedsFromBody(n.getBody(), attachmentId));
        n.setUpdatedAt(Instant.now());
        attachmentRepository.deleteById(attachmentId);
        noteRepository.save(n);
    }

    /** Remove HTML and markdown embeds that point at this attachment so delete cannot leave broken images. */
    static String stripAttachmentEmbedsFromBody(String body, long attachmentId) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        String quoted = Pattern.quote("/api/life/notes/attachments/" + attachmentId + "/file");
        String html = "(?i)<img\\b[^>]*\\bsrc=[\"'][^\"']*" + quoted + "[^\"']*[\"'][^>]*>";
        String markdown = "(?i)!\\[[^\\]]*\\]\\([^)]*" + quoted + "[^)]*\\)";
        String out = body.replaceAll(html, "").replaceAll(markdown, "");
        return out.replaceAll("\\n{3,}", "\n\n");
    }

    public record AttachmentFile(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public AttachmentFile readAttachmentFile(long attachmentId) {
        LifeMonthNoteAttachment a = attachmentRepository
                .findByIdWithNote(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        assertRowAccess(a.getNote().getOwnerUserId());
        try {
            byte[] body = blobStore.readAllBytes(a.getStorageKey());
            var normalized =
                    HeicImageNormalizer.normalize(a.getOriginalFilename(), a.getContentType(), body);
            return new AttachmentFile(
                    normalized.contentType() != null
                            ? normalized.contentType()
                            : "application/octet-stream",
                    normalized.filename(),
                    normalized.bytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertRowAccess(Long ownerUserId) {
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private LifeMonthNoteDto toDto(LifeMonthNote n) {
        List<LifeMonthNoteAttachment> atts = new ArrayList<>(n.getAttachments());
        atts.sort(Comparator.comparing(LifeMonthNoteAttachment::getId));
        List<LifeMonthNoteAttachmentDto> attDtos = atts.stream().map(this::toAttachmentDto).toList();
        return new LifeMonthNoteDto(
                n.getId(),
                n.getOwnerUserId(),
                n.getYear(),
                n.getMonth(),
                n.getSubject(),
                n.getBody() == null ? "" : n.getBody(),
                attDtos,
                n.getCreatedAt(),
                n.getUpdatedAt());
    }

    private LifeMonthNoteAttachmentDto toAttachmentDto(LifeMonthNoteAttachment a) {
        return new LifeMonthNoteAttachmentDto(
                a.getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                "/api/life/notes/attachments/" + a.getId() + "/file");
    }
}
