package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.BankingCreateInstitutionRequestDto;
import com.svp.tracker.finance.dto.BankingCreateInstitutionTypeRequestDto;
import com.svp.tracker.finance.dto.BankingImportFileDto;
import com.svp.tracker.finance.dto.BankingImportResultDto;
import com.svp.tracker.finance.dto.BankingInstitutionDto;
import com.svp.tracker.finance.dto.BankingInstitutionTypeDto;
import com.svp.tracker.finance.dto.BankingPutInstitutionRequestDto;
import com.svp.tracker.finance.dto.BankingLedgerDto;
import com.svp.tracker.finance.dto.BankingLedgerRange;
import com.svp.tracker.finance.service.BankingService;
import com.svp.tracker.finance.service.BankingService.BankingFileContent;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/finance/banking")
@RequiredArgsConstructor
@Slf4j
public class BankingController {

    private final BankingService bankingService;

    @GetMapping("/institutions")
    public List<BankingInstitutionDto> institutions() {
        return bankingService.listInstitutions();
    }

    @PostMapping("/institutions")
    @ResponseStatus(HttpStatus.CREATED)
    public BankingInstitutionDto createInstitution(@Valid @RequestBody BankingCreateInstitutionRequestDto body) {
        return bankingService.createInstitution(body);
    }

    @PutMapping("/institutions/{id}")
    public BankingInstitutionDto updateInstitution(
            @PathVariable long id, @Valid @RequestBody BankingPutInstitutionRequestDto body) {
        return bankingService.updateInstitution(id, body);
    }

    @GetMapping("/institution-types")
    public List<BankingInstitutionTypeDto> institutionTypes() {
        return bankingService.listInstitutionTypes();
    }

    @PostMapping("/institution-types")
    @ResponseStatus(HttpStatus.CREATED)
    public BankingInstitutionTypeDto createInstitutionType(
            @Valid @RequestBody BankingCreateInstitutionTypeRequestDto body) {
        return bankingService.createInstitutionType(body);
    }

    @DeleteMapping("/institution-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInstitutionType(@PathVariable long id) {
        bankingService.deleteInstitutionType(id);
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BankingImportResultDto importUpload(
            @RequestParam("institutionId") long institutionId, @RequestParam("file") MultipartFile file)
            throws IOException {
        log.info(
                "POST /api/finance/banking/imports institutionId={} filename={} size={}",
                institutionId,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);
        return bankingService.importFile(institutionId, file);
    }

    @GetMapping("/ledger")
    public BankingLedgerDto ledger(
            @RequestParam("range") BankingLedgerRange range,
            @RequestParam int year,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "quarter", required = false) Integer quarter,
            @RequestParam(name = "weekStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate weekStart,
            @RequestParam(name = "institutionId", required = false) Long institutionId,
            @RequestParam(name = "institutionTypeId", required = false) Long institutionTypeId) {
        validateLedgerParams(range, year, month, quarter, weekStart);
        return bankingService.ledger(range, year, month, quarter, weekStart, institutionId, institutionTypeId);
    }

    /** Admin-style listing of uploads across a date span (same owner scope as ledger import files). */
    @GetMapping("/import-files")
    public List<BankingImportFileDto> importFiles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "institutionId", required = false) Long institutionId,
            @RequestParam(name = "institutionTypeId", required = false) Long institutionTypeId) {
        return bankingService.listImportFilesInRange(from, to, institutionId, institutionTypeId);
    }

    @DeleteMapping("/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable long id) {
        bankingService.deleteImportFile(id);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable long id, @RequestParam(defaultValue = "attachment") String disposition) {
        BankingFileContent f = bankingService.readFile(id);
        String mode = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }

    private static void validateLedgerParams(
            BankingLedgerRange range, int year, Integer month, Integer quarter, LocalDate weekStart) {
        if (year < 1990 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year out of range");
        }
        switch (range) {
            case WEEK, BIWEEK -> {
                // weekStart optional — service defaults to current week's Monday
                if (weekStart != null && (weekStart.getYear() < 1990 || weekStart.getYear() > 2100)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weekStart out of range");
                }
            }
            case MONTH -> {
                if (month == null || month < 1 || month > 12) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month (1-12) required for MONTH range");
                }
            }
            case QUARTER -> {
                if (quarter == null || quarter < 1 || quarter > 4) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quarter (1-4) required for QUARTER range");
                }
            }
            case YEAR -> {
                // month/quarter ignored
            }
        }
    }
}
