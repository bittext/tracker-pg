package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceCreditCardDto;
import com.svp.tracker.finance.dto.FinanceCreditCardOptionsDto;
import com.svp.tracker.finance.dto.FinanceCreditCardRequestDto;
import com.svp.tracker.finance.dto.FinanceCreditCardStatementDto;
import com.svp.tracker.finance.dto.FinanceCreditCardStatementRequestDto;
import com.svp.tracker.finance.dto.FinanceCreditCardSummaryDto;
import com.svp.tracker.finance.service.FinanceCreditCardService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/credit-cards")
@RequiredArgsConstructor
@Slf4j
public class FinanceCreditCardController {

    private final FinanceCreditCardService creditCardService;

    @GetMapping("/options")
    public FinanceCreditCardOptionsDto options() {
        return creditCardService.options();
    }

    @GetMapping("/summary")
    public FinanceCreditCardSummaryDto summary() {
        return creditCardService.summaryForCurrentUser();
    }

    @GetMapping
    public List<FinanceCreditCardDto> list() {
        return creditCardService.listForCurrentUser();
    }

    @GetMapping("/{id}")
    public FinanceCreditCardDto get(@PathVariable long id) {
        return creditCardService.getForCurrentUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceCreditCardDto create(@RequestBody FinanceCreditCardRequestDto body) {
        log.info("POST /api/finance/credit-cards institution={} card={}", body.institution(), body.cardName());
        return creditCardService.createForCurrentUser(body);
    }

    @PutMapping("/{id}")
    public FinanceCreditCardDto update(@PathVariable long id, @RequestBody FinanceCreditCardRequestDto body) {
        log.info("PUT /api/finance/credit-cards/{} card={}", id, body.cardName());
        return creditCardService.updateForCurrentUser(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/credit-cards/{}", id);
        creditCardService.deleteForCurrentUser(id);
    }

    @GetMapping("/{cardId}/statements")
    public List<FinanceCreditCardStatementDto> listStatements(@PathVariable long cardId) {
        return creditCardService.listStatementsForCurrentUser(cardId);
    }

    @PostMapping("/{cardId}/statements")
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceCreditCardStatementDto createStatement(
            @PathVariable long cardId, @RequestBody FinanceCreditCardStatementRequestDto body) {
        log.info("POST /api/finance/credit-cards/{}/statements date={}", cardId, body.statementDate());
        return creditCardService.createStatementForCurrentUser(cardId, body);
    }

    @PutMapping("/{cardId}/statements/{statementId}")
    public FinanceCreditCardStatementDto updateStatement(
            @PathVariable long cardId,
            @PathVariable long statementId,
            @RequestBody FinanceCreditCardStatementRequestDto body) {
        log.info("PUT /api/finance/credit-cards/{}/statements/{}", cardId, statementId);
        return creditCardService.updateStatementForCurrentUser(cardId, statementId, body);
    }

    @DeleteMapping("/{cardId}/statements/{statementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStatement(@PathVariable long cardId, @PathVariable long statementId) {
        log.info("DELETE /api/finance/credit-cards/{}/statements/{}", cardId, statementId);
        creditCardService.deleteStatementForCurrentUser(cardId, statementId);
    }
}
