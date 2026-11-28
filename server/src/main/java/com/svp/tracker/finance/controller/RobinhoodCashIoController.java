package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodCashIoAccountDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoCalendarDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoDailyHistoryDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoEntryDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoImportDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoImportRequestDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoLedgerDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoRequestDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoYtdDto;
import com.svp.tracker.finance.service.RobinhoodCashIoDailyService;
import com.svp.tracker.finance.service.RobinhoodCashIoImportService;
import com.svp.tracker.finance.service.RobinhoodCashIoService;
import com.svp.tracker.finance.service.RobinhoodCashIoYtdService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping({"/api/finance/robinhood/cash-io", "/api/markets/cash-io"})
@RequiredArgsConstructor
@Slf4j
public class RobinhoodCashIoController {

    private static final Object IMPORT_LOCK = new Object();

    private final RobinhoodCashIoService cashIoService;
    private final RobinhoodCashIoYtdService cashIoYtdService;
    private final RobinhoodCashIoDailyService cashIoDailyService;
    private final RobinhoodCashIoImportService cashIoImportService;

    @GetMapping("/accounts")
    public List<RobinhoodCashIoAccountDto> accounts() {
        return cashIoService.listAccounts();
    }

    /** Names that can be picked as an origin or receiving account: your accounts plus saved counterparties. */
    @GetMapping("/parties")
    public List<String> parties() {
        return cashIoService.listParties();
    }

    @GetMapping
    public RobinhoodCashIoLedgerDto ledger(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String accountSuffix,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String receiver) {
        return cashIoService.ledger(year, month, accountSuffix, origin, receiver);
    }

    @GetMapping("/calendar")
    public RobinhoodCashIoCalendarDto calendar(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String accountSuffix,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String receiver) {
        return cashIoService.calendar(year, month, accountSuffix, origin, receiver);
    }

    @GetMapping("/ytd")
    public RobinhoodCashIoYtdDto ytd(
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "3370") String accountSuffix) {
        return cashIoYtdService.ytd(year, accountSuffix);
    }

    @GetMapping("/daily")
    public RobinhoodCashIoDailyHistoryDto daily(
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "3370") String accountSuffix) {
        return cashIoDailyService.history(year, accountSuffix);
    }

    /** Shows what importing a Robinhood CSV would add, without saving anything. */
    @PostMapping("/import/preview")
    public RobinhoodCashIoImportDto importPreview(@RequestBody RobinhoodCashIoImportRequestDto body) {
        log.info("POST cash-io/import/preview suffix={}", body != null ? body.accountSuffix() : null);
        return cashIoImportService.preview(body);
    }

    /** Adds the new transfers from a Robinhood CSV to the ledger; rows already there are skipped. */
    @PostMapping("/import")
    public RobinhoodCashIoImportDto importRows(@RequestBody RobinhoodCashIoImportRequestDto body) {
        log.info("POST cash-io/import suffix={}", body != null ? body.accountSuffix() : null);
        // One import at a time: a second request (a double click, a retry) waits for the first to commit, then
        // sees its entries and recognizes them as duplicates instead of adding them again.
        synchronized (IMPORT_LOCK) {
            return cashIoImportService.importRows(body);
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RobinhoodCashIoEntryDto create(@RequestBody RobinhoodCashIoRequestDto body) {
        log.info(
                "POST /api/finance/robinhood/cash-io suffix={} date={} dir={}",
                body != null ? body.accountSuffix() : null,
                body != null ? body.activityDate() : null,
                body != null ? body.direction() : null);
        return cashIoService.create(body);
    }

    @PutMapping("/{id}")
    public RobinhoodCashIoEntryDto update(@PathVariable long id, @RequestBody RobinhoodCashIoRequestDto body) {
        log.info("PUT /api/finance/robinhood/cash-io/{}", id);
        return cashIoService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/robinhood/cash-io/{}", id);
        cashIoService.delete(id);
    }
}
