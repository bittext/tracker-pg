package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.BankingInstitution;
import com.svp.tracker.finance.domain.FinanceCreditCard;
import com.svp.tracker.finance.domain.FinanceCreditCardStatement;
import com.svp.tracker.finance.dto.FinanceCreditCardBankingInstitutionOptionDto;
import com.svp.tracker.finance.dto.FinanceCreditCardDto;
import com.svp.tracker.finance.dto.FinanceCreditCardOptionsDto;
import com.svp.tracker.finance.dto.FinanceCreditCardRequestDto;
import com.svp.tracker.finance.dto.FinanceCreditCardStatementDto;
import com.svp.tracker.finance.dto.FinanceCreditCardStatementRequestDto;
import com.svp.tracker.finance.dto.FinanceCreditCardSummaryDto;
import com.svp.tracker.finance.repository.BankingInstitutionRepository;
import com.svp.tracker.finance.repository.FinanceCreditCardRepository;
import com.svp.tracker.finance.repository.FinanceCreditCardStatementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinanceCreditCardService {

    private final CurrentUserService currentUser;
    private final FinanceCreditCardRepository cardRepository;
    private final FinanceCreditCardStatementRepository statementRepository;
    private final BankingInstitutionRepository bankingInstitutionRepository;
    private final FinanceEntryDocumentService entryDocumentService;

    @Transactional(readOnly = true)
    public FinanceCreditCardOptionsDto options() {
        long uid = currentUser.requireUserId();
        List<FinanceCreditCardBankingInstitutionOptionDto> institutions =
                bankingInstitutionRepository.findByOwnerUserIdOrderByNameAsc(uid).stream()
                        .map(this::toBankingOption)
                        .toList();
        return new FinanceCreditCardOptionsDto(institutions);
    }

    @Transactional(readOnly = true)
    public FinanceCreditCardSummaryDto summaryForCurrentUser() {
        long uid = currentUser.requireUserId();
        List<FinanceCreditCard> cards = cardRepository.findByOwnerUserIdOrderByInstitutionAscCardNameAsc(uid);
        BigDecimal totalLimit = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        boolean hasLimit = false;
        for (FinanceCreditCard card : cards) {
            if (card.getCreditLimit() != null) {
                hasLimit = true;
                totalLimit = totalLimit.add(card.getCreditLimit());
            }
            if (card.getCurrentBalance() != null) {
                totalBalance = totalBalance.add(card.getCurrentBalance());
            }
        }
        BigDecimal overallUtil = hasLimit ? FinanceCreditHealth.utilizationPct(totalBalance, totalLimit) : null;
        return new FinanceCreditCardSummaryDto(
                cards.size(), hasLimit ? totalLimit : null, totalBalance, overallUtil, FinanceCreditHealth.healthLabel(overallUtil));
    }

    @Transactional(readOnly = true)
    public List<FinanceCreditCardDto> listForCurrentUser() {
        long uid = currentUser.requireUserId();
        Map<Long, String> bankingNames = bankingNameMap(uid);
        return cardRepository.findByOwnerUserIdOrderByInstitutionAscCardNameAsc(uid).stream()
                .map(c -> toDto(c, bankingNames, uid))
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanceCreditCardDto getForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        return toDto(requireOwnedCard(id, uid), bankingNameMap(uid), uid);
    }

    @Transactional
    public FinanceCreditCardDto createForCurrentUser(FinanceCreditCardRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceCreditCard row = new FinanceCreditCard();
        row.setOwnerUserId(uid);
        apply(row, req, uid);
        return toDto(cardRepository.save(row), bankingNameMap(uid), uid);
    }

    @Transactional
    public FinanceCreditCardDto updateForCurrentUser(long id, FinanceCreditCardRequestDto req) {
        long uid = currentUser.requireUserId();
        FinanceCreditCard row = requireOwnedCard(id, uid);
        apply(row, req, uid);
        row.setUpdatedAt(Instant.now());
        return toDto(cardRepository.save(row), bankingNameMap(uid), uid);
    }

    @Transactional
    public void deleteForCurrentUser(long id) {
        long uid = currentUser.requireUserId();
        requireOwnedCard(id, uid);
        entryDocumentService.deleteAllForEntity(FinanceEntryEntityType.CREDIT_CARD, id, uid);
        cardRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<FinanceCreditCardStatementDto> listStatementsForCurrentUser(long cardId) {
        long uid = currentUser.requireUserId();
        requireOwnedCard(cardId, uid);
        return statementRepository.findByCreditCardIdAndOwnerUserIdOrderByStatementDateDesc(cardId, uid).stream()
                .map(this::toStatementDto)
                .toList();
    }

    @Transactional
    public FinanceCreditCardStatementDto createStatementForCurrentUser(
            long cardId, FinanceCreditCardStatementRequestDto req) {
        long uid = currentUser.requireUserId();
        requireOwnedCard(cardId, uid);
        FinanceCreditCardStatement row = new FinanceCreditCardStatement();
        row.setCreditCardId(cardId);
        row.setOwnerUserId(uid);
        applyStatement(row, req);
        return toStatementDto(statementRepository.save(row));
    }

    @Transactional
    public FinanceCreditCardStatementDto updateStatementForCurrentUser(
            long cardId, long statementId, FinanceCreditCardStatementRequestDto req) {
        long uid = currentUser.requireUserId();
        requireOwnedCard(cardId, uid);
        FinanceCreditCardStatement row = requireOwnedStatement(statementId, cardId, uid);
        applyStatement(row, req);
        row.setUpdatedAt(Instant.now());
        return toStatementDto(statementRepository.save(row));
    }

    @Transactional
    public void deleteStatementForCurrentUser(long cardId, long statementId) {
        long uid = currentUser.requireUserId();
        requireOwnedCard(cardId, uid);
        statementRepository.delete(requireOwnedStatement(statementId, cardId, uid));
    }

    private FinanceCreditCard requireOwnedCard(long id, long uid) {
        return cardRepository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit card not found"));
    }

    private FinanceCreditCardStatement requireOwnedStatement(long id, long cardId, long uid) {
        return statementRepository
                .findByIdAndCreditCardIdAndOwnerUserId(id, cardId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));
    }

    private void apply(FinanceCreditCard row, FinanceCreditCardRequestDto req, long uid) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String institution = clean(req.institution());
        if (institution.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution is required");
        }
        String cardName = clean(req.cardName());
        if (cardName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card name is required");
        }
        row.setInstitution(institution);
        row.setCardName(cardName);
        String lastFour = clean(req.lastFour());
        if (!lastFour.isBlank() && !lastFour.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last four must be exactly 4 digits");
        }
        row.setLastFour(lastFour.isBlank() ? null : lastFour);
        row.setCreditLimit(req.creditLimit());
        row.setCurrentBalance(req.currentBalance());
        row.setApr(req.apr());
        row.setStatementBalance(req.statementBalance());
        row.setStatementDate(req.statementDate());
        row.setPaymentDueDate(req.paymentDueDate());
        row.setNotes(emptyToNull(req.notes()));
        Long bankingId = req.bankingInstitutionId();
        if (bankingId != null && bankingId > 0) {
            if (!bankingInstitutionRepository.existsByIdAndOwnerUserId(bankingId, uid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Banking institution not found");
            }
            row.setBankingInstitutionId(bankingId);
        } else {
            row.setBankingInstitutionId(null);
        }
    }

    private void applyStatement(FinanceCreditCardStatement row, FinanceCreditCardStatementRequestDto req) {
        if (req == null || req.statementDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement date is required");
        }
        row.setStatementDate(req.statementDate());
        row.setStatementBalance(req.statementBalance());
        row.setMinimumPayment(req.minimumPayment());
        row.setPaymentDueDate(req.paymentDueDate());
        row.setNotes(emptyToNull(req.notes()));
    }

    private FinanceCreditCardDto toDto(FinanceCreditCard row, Map<Long, String> bankingNames, long ownerUserId) {
        BigDecimal util = FinanceCreditHealth.utilizationPct(row.getCurrentBalance(), row.getCreditLimit());
        Long bankingId = row.getBankingInstitutionId();
        String bankingName = bankingId != null ? bankingNames.getOrDefault(bankingId, "") : "";
        int documentCount =
                (int) entryDocumentService.countForEntity(FinanceEntryEntityType.CREDIT_CARD, row.getId(), ownerUserId);
        return new FinanceCreditCardDto(
                row.getId(),
                row.getInstitution(),
                row.getCardName(),
                row.getLastFour() == null ? "" : row.getLastFour(),
                row.getCreditLimit(),
                row.getCurrentBalance(),
                row.getApr(),
                row.getStatementBalance(),
                row.getStatementDate(),
                row.getPaymentDueDate(),
                bankingId,
                bankingName,
                util,
                FinanceCreditHealth.availableCredit(row.getCurrentBalance(), row.getCreditLimit()),
                FinanceCreditHealth.healthLabel(util),
                row.getNotes() == null ? "" : row.getNotes(),
                documentCount,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private FinanceCreditCardStatementDto toStatementDto(FinanceCreditCardStatement row) {
        return new FinanceCreditCardStatementDto(
                row.getId(),
                row.getCreditCardId(),
                row.getStatementDate(),
                row.getStatementBalance(),
                row.getMinimumPayment(),
                row.getPaymentDueDate(),
                row.getNotes() == null ? "" : row.getNotes(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private FinanceCreditCardBankingInstitutionOptionDto toBankingOption(BankingInstitution inst) {
        String typeName =
                inst.getInstitutionType() != null ? inst.getInstitutionType().getName() : "";
        return new FinanceCreditCardBankingInstitutionOptionDto(inst.getId(), inst.getName(), typeName);
    }

    private Map<Long, String> bankingNameMap(long uid) {
        Map<Long, String> map = new HashMap<>();
        for (BankingInstitution inst : bankingInstitutionRepository.findByOwnerUserIdOrderByNameAsc(uid)) {
            map.put(inst.getId(), inst.getName());
        }
        return map;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
