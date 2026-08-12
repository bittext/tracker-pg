package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodTradeInterest;
import com.svp.tracker.finance.dto.RobinhoodTradeInterestDto;
import com.svp.tracker.finance.dto.RobinhoodTradeInterestRequestDto;
import com.svp.tracker.finance.repository.RobinhoodTradeInterestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RobinhoodTradeInterestService {

    private static final String KIND_STOCK = "STOCK";
    private static final String KIND_OPTION = "OPTION";

    private final CurrentUserService currentUser;
    private final RobinhoodTradeInterestRepository repository;

    @Transactional(readOnly = true)
    public List<RobinhoodTradeInterestDto> list(String status) {
        long uid = currentUser.requireUserId();
        String normalized = normalizeOptionalStatus(status);
        List<RobinhoodTradeInterest> rows =
                normalized == null
                        ? repository.findByOwnerUserIdOrderByPlannedAtDescIdDesc(uid)
                        : repository.findByOwnerUserIdAndStatusOrderByPlannedAtDescIdDesc(uid, normalized);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public RobinhoodTradeInterestDto create(RobinhoodTradeInterestRequestDto body) {
        long uid = currentUser.requireUserId();
        ValidatedRequest req = validate(body, true, "OPEN");
        Instant now = Instant.now();
        RobinhoodTradeInterest row = new RobinhoodTradeInterest();
        row.setOwnerUserId(uid);
        apply(row, req);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return toDto(repository.save(row));
    }

    @Transactional
    public RobinhoodTradeInterestDto update(long id, RobinhoodTradeInterestRequestDto body) {
        long uid = currentUser.requireUserId();
        RobinhoodTradeInterest row = requireOwned(id, uid);
        ValidatedRequest req = validate(body, false, row.getStatus());
        apply(row, req);
        row.setUpdatedAt(Instant.now());
        return toDto(repository.save(row));
    }

    @Transactional
    public void delete(long id) {
        long uid = currentUser.requireUserId();
        repository.delete(requireOwned(id, uid));
    }

    private RobinhoodTradeInterest requireOwned(long id, long uid) {
        return repository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade interest not found"));
    }

    private static void apply(RobinhoodTradeInterest row, ValidatedRequest req) {
        row.setInstrumentKind(req.kind());
        row.setSymbol(req.symbol());
        row.setPlannedAt(req.plannedAt());
        row.setUnderlyingPrice(req.underlyingPrice());
        row.setContractTargetPrice(req.contractTargetPrice());
        row.setExpiryDate(req.expiryDate());
        row.setNote(req.note());
        row.setStatus(req.status());
    }

    private ValidatedRequest validate(
            RobinhoodTradeInterestRequestDto body, boolean creating, String existingStatus) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String kind = normalizeKind(body.instrumentKind());
        String symbol = body.symbol() == null ? "" : body.symbol().trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        if (symbol.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol too long");
        }
        if (body.plannedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plannedAt (date and time) is required");
        }
        BigDecimal underlying = requirePositive(body.underlyingPrice(), "underlyingPrice");
        BigDecimal contract = null;
        java.time.LocalDate expiry = null;
        if (KIND_OPTION.equals(kind)) {
            contract = requirePositive(body.contractTargetPrice(), "contractTargetPrice");
            if (body.expiryDate() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiryDate is required for options");
            }
            expiry = body.expiryDate();
        }
        String note = body.note() == null ? null : body.note().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        String status;
        if (body.status() == null || body.status().isBlank()) {
            status = creating ? "OPEN" : (existingStatus == null ? "OPEN" : existingStatus);
        } else {
            status = normalizeStatus(body.status());
        }
        return new ValidatedRequest(kind, symbol, body.plannedAt(), underlying, contract, expiry, note, status);
    }

    private static String normalizeKind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instrumentKind is required (STOCK or OPTION)");
        }
        String k = raw.trim().toUpperCase(Locale.ROOT);
        if ("EQUITY".equals(k) || "SHARE".equals(k) || "SHARES".equals(k)) {
            k = KIND_STOCK;
        }
        if ("OPT".equals(k) || "OPTIONS".equals(k) || "CONTRACT".equals(k)) {
            k = KIND_OPTION;
        }
        if (!KIND_STOCK.equals(k) && !KIND_OPTION.equals(k)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instrumentKind must be STOCK or OPTION");
        }
        return k;
    }

    private static String normalizeStatus(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "OPEN", "INTERESTED", "PLANNED" -> "OPEN";
            case "TAKEN", "ENTERED", "DONE" -> "TAKEN";
            case "PASSED", "SKIPPED", "CANCELLED", "CANCELED" -> "PASSED";
            case "EXPIRED" -> "EXPIRED";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "status must be OPEN, TAKEN, PASSED, or EXPIRED");
        };
    }

    private static String normalizeOptionalStatus(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return normalizeStatus(raw);
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be greater than zero");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private RobinhoodTradeInterestDto toDto(RobinhoodTradeInterest row) {
        return new RobinhoodTradeInterestDto(
                row.getId(),
                row.getInstrumentKind(),
                row.getSymbol(),
                row.getPlannedAt(),
                row.getUnderlyingPrice(),
                row.getContractTargetPrice(),
                row.getExpiryDate(),
                row.getNote(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private record ValidatedRequest(
            String kind,
            String symbol,
            Instant plannedAt,
            BigDecimal underlyingPrice,
            BigDecimal contractTargetPrice,
            java.time.LocalDate expiryDate,
            String note,
            String status) {}
}
