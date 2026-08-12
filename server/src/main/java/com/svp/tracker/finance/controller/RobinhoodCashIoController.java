package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodCashIoAccountDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoCalendarDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoEntryDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoLedgerDto;
import com.svp.tracker.finance.dto.RobinhoodCashIoRequestDto;
import com.svp.tracker.finance.service.RobinhoodCashIoService;
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

    private final RobinhoodCashIoService cashIoService;

    @GetMapping("/accounts")
    public List<RobinhoodCashIoAccountDto> accounts() {
        return cashIoService.listAccounts();
    }

    @GetMapping
    public RobinhoodCashIoLedgerDto ledger(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String accountSuffix) {
        return cashIoService.ledger(year, month, accountSuffix);
    }

    @GetMapping("/calendar")
    public RobinhoodCashIoCalendarDto calendar(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String accountSuffix) {
        return cashIoService.calendar(year, month, accountSuffix);
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
