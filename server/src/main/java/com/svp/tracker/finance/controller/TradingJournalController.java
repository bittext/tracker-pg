package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.domain.TradingJournalAttachment;
import com.svp.tracker.finance.dto.TradingJournalAiDraftDto;
import com.svp.tracker.finance.dto.TradingJournalAttachmentDto;
import com.svp.tracker.finance.dto.TradingJournalDayDetailDto;
import com.svp.tracker.finance.dto.TradingJournalEntryDto;
import com.svp.tracker.finance.dto.TradingJournalListDto;
import com.svp.tracker.finance.dto.TradingJournalRefDto;
import com.svp.tracker.finance.dto.TradingJournalRefRequestDto;
import com.svp.tracker.finance.dto.TradingJournalUpdateRequestDto;
import com.svp.tracker.finance.service.TradingJournalService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/markets/trading-journal", "/api/finance/robinhood/trading-journal"})
@RequiredArgsConstructor
@Slf4j
public class TradingJournalController {

    private final TradingJournalService tradingJournalService;

    @GetMapping
    public TradingJournalListDto list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String q) {
        return tradingJournalService.list(year, month, q);
    }

    @GetMapping("/dates")
    public List<LocalDate> dates(
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {
        return tradingJournalService.journalDates(year, month);
    }

    @GetMapping("/{date}")
    public TradingJournalDayDetailDto getDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tradingJournalService.getDay(date);
    }

    @PostMapping("/{date}")
    public TradingJournalDayDetailDto openOrCreate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("POST trading-journal/{}", date);
        return tradingJournalService.getOrCreateDay(date);
    }

    @PutMapping("/{date}")
    public TradingJournalEntryDto update(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody TradingJournalUpdateRequestDto body) {
        return tradingJournalService.update(date, body);
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        tradingJournalService.delete(date);
    }

    @PostMapping("/{date}/import-summary")
    public TradingJournalEntryDto importSummary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tradingJournalService.importCallSummary(date);
    }

    @PostMapping("/{date}/pin-close")
    public TradingJournalEntryDto pinClose(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tradingJournalService.pinClose(date);
    }

    @PostMapping("/{date}/ai-draft")
    public TradingJournalAiDraftDto aiDraft(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tradingJournalService.aiDraft(date);
    }

    @PostMapping("/{date}/refs")
    @ResponseStatus(HttpStatus.CREATED)
    public TradingJournalRefDto addRef(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody TradingJournalRefRequestDto body) {
        return tradingJournalService.addRef(date, body);
    }

    @DeleteMapping("/refs/{refId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRef(@PathVariable long refId) {
        tradingJournalService.deleteRef(refId);
    }

    @PostMapping(path = "/{date}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TradingJournalAttachmentDto addAttachment(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientLastModifiedMs", required = false) Long clientLastModifiedMs) {
        return tradingJournalService.addAttachment(date, file, clientLastModifiedMs);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable long attachmentId) {
        tradingJournalService.deleteAttachment(attachmentId);
    }

    @GetMapping("/attachments/{attachmentId}/file")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable long attachmentId,
            @RequestParam(defaultValue = "attachment") String disposition) {
        TradingJournalAttachment meta = tradingJournalService.requireAttachmentMeta(attachmentId);
        byte[] bytes = tradingJournalService.readAttachmentBytes(attachmentId);
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (meta.getContentType() != null && !meta.getContentType().isBlank()) {
            try {
                type = MediaType.parseMediaType(meta.getContentType());
            } catch (Exception ignored) {
                /* keep octet-stream */
            }
        }
        String safeName = meta.getOriginalFilename().replace("\"", "");
        String disp = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disp + "; filename=\"" + safeName + "\"")
                .contentType(type)
                .body(bytes);
    }
}
