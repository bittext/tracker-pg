package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.BankingPlaidExchangeRequestDto;
import com.svp.tracker.finance.dto.BankingPlaidExchangeResponseDto;
import com.svp.tracker.finance.dto.BankingPlaidLinkTokenResponseDto;
import com.svp.tracker.finance.dto.BankingPlaidStatusDto;
import com.svp.tracker.finance.dto.BankingPlaidSyncRequestDto;
import com.svp.tracker.finance.dto.BankingPlaidSyncResponseDto;
import com.svp.tracker.finance.service.BankingPlaidService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/banking/plaid")
@RequiredArgsConstructor
@Slf4j
public class BankingPlaidController {

    private final BankingPlaidService bankingPlaidService;

    @GetMapping("/status")
    public BankingPlaidStatusDto status(@RequestParam long institutionId) {
        return bankingPlaidService.status(institutionId);
    }

    /**
     * Returns a short-lived {@code link_token} for Plaid Link (browser). Requires Plaid {@code client_id} /
     * {@code secret} and {@code tracker.finance.banking.plaid.enabled=true}.
     */
    @PostMapping("/link-token")
    public BankingPlaidLinkTokenResponseDto linkToken(@RequestParam long institutionId) {
        return bankingPlaidService.createLinkToken(institutionId);
    }

    /** Exchange Link {@code public_token} for an access token and persist it against the banking institution. */
    @PostMapping("/exchange")
    public BankingPlaidExchangeResponseDto exchange(@Valid @RequestBody BankingPlaidExchangeRequestDto body) {
        return bankingPlaidService.exchangePublicToken(body);
    }

    @DeleteMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@RequestParam long institutionId) {
        bankingPlaidService.unlink(institutionId);
    }

    /**
     * Pulls Plaid transactions for the date range, writes a QFX-compatible OFX file under {@code
     * import-directory}/{@code plaid}/…, then imports using the same dedupe rules as manual uploads (per-row skips
     * recorded on {@code banking_import_files}).
     */
    @PostMapping("/sync")
    public BankingPlaidSyncResponseDto sync(@Valid @RequestBody BankingPlaidSyncRequestDto body) throws IOException {
        log.info(
                "POST /api/finance/banking/plaid/sync institutionId={} {}..{}",
                body.institutionId(),
                body.startDate(),
                body.endDate());
        return bankingPlaidService.sync(body);
    }
}
