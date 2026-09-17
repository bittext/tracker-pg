package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.FinanceTaxDeskRhRealized;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.dto.FinanceTaxDeskRhAccountRealizedDto;
import com.svp.tracker.finance.repository.FinanceTaxDeskRhRealizedRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Robinhood calendar-window realized P&L for Tax desk (Individual / Agentic / Ammu). Broker
 * screen figure, not Form 1099-B.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodBrokerRealizedPnlService {

    static final List<String> TAX_DESK_SUFFIXES = List.of("3370", "3550", "8696");
    static final String INDIVIDUAL_SUFFIX = "3370";

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final FinanceTaxDeskRhRealizedRepository realizedRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record Fetched(
            LocalDate startDate,
            LocalDate asOf,
            BigDecimal total,
            List<FinanceTaxDeskRhAccountRealizedDto> accounts,
            List<String> warnings) {}

    /**
     * Sidecar calendar-window fetch (HTTP). Individual ••••3370 must be present or this is empty.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<Fetched> fetchLive(long owner, int taxYear, LocalDate asOf) {
        if (!agenticProps.serviceConfigured()) {
            return Optional.empty();
        }
        RobinhoodAgenticConnection conn = connectionRepository.findByOwnerUserId(owner).orElse(null);
        if (conn == null) {
            return Optional.empty();
        }
        LocalDate start = LocalDate.of(taxYear, 1, 1);
        try {
            JsonNode root = tokenService.fetchRealizedPnl(
                    conn, start.toString(), asOf.toString(), TAX_DESK_SUFFIXES);
            Optional<Fetched> parsed = parseSidecar(root, start, asOf);
            if (parsed.isEmpty()) {
                log.warn("Robinhood realized P&L for user {} on {} had no usable Individual total", owner, asOf);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Robinhood realized P&L fetch failed for user {}: {}", owner, e.toString());
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(long owner, int taxYear, Fetched fetched) {
        Instant now = Instant.now();
        FinanceTaxDeskRhRealized row = realizedRepository
                .findByOwnerUserIdAndTaxYearAndAsOfDate(owner, taxYear, fetched.asOf())
                .orElseGet(FinanceTaxDeskRhRealized::new);
        if (row.getId() == null) {
            row.setOwnerUserId(owner);
            row.setTaxYear(taxYear);
            row.setAsOfDate(fetched.asOf());
            row.setCreatedAt(now);
        }
        row.setStartDate(fetched.startDate());
        row.setEndDate(fetched.asOf());
        row.setTotalRealized(fetched.total());
        row.setAccountsJson(writeAccounts(fetched.accounts()));
        row.setWarnings(String.join("\n", fetched.warnings()));
        row.setUpdatedAt(now);
        realizedRepository.save(row);
    }

    public Optional<Fetched> storedOnOrBefore(long owner, int taxYear, LocalDate asOf) {
        return realizedRepository
                .findFirstByOwnerUserIdAndTaxYearAndAsOfDateLessThanEqualOrderByAsOfDateDesc(owner, taxYear, asOf)
                .flatMap(this::fromRow);
    }

    private Optional<Fetched> fromRow(FinanceTaxDeskRhRealized row) {
        List<FinanceTaxDeskRhAccountRealizedDto> accounts = readAccounts(row.getAccountsJson());
        if (!hasIndividual(accounts)) {
            return Optional.empty();
        }
        BigDecimal total = row.getTotalRealized() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : row.getTotalRealized().setScale(2, RoundingMode.HALF_UP);
        List<String> warnings = new ArrayList<>();
        if (row.getWarnings() != null && !row.getWarnings().isBlank()) {
            warnings.add(row.getWarnings());
        }
        return Optional.of(new Fetched(row.getStartDate(), row.getAsOfDate(), total, accounts, warnings));
    }

    static Optional<Fetched> parseSidecar(JsonNode root, LocalDate start, LocalDate asOf) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return Optional.empty();
        }
        List<String> warnings = new ArrayList<>();
        JsonNode warnNode = root.get("warnings");
        if (warnNode != null && warnNode.isArray()) {
            for (JsonNode w : warnNode) {
                if (w != null && w.isTextual() && !w.asText().isBlank()) {
                    warnings.add(w.asText());
                }
            }
        }
        List<FinanceTaxDeskRhAccountRealizedDto> accounts = new ArrayList<>();
        JsonNode rows = root.get("accounts");
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                if (row == null || !row.isObject()) {
                    continue;
                }
                String suffix = text(row, "suffix", "account_number_last4");
                if (suffix == null || suffix.isBlank()) {
                    continue;
                }
                suffix = suffix.replaceAll("\\D", "");
                if (suffix.length() > 4) {
                    suffix = suffix.substring(suffix.length() - 4);
                }
                String label = text(row, "label");
                if (label == null || label.isBlank()) {
                    label = suffix;
                }
                BigDecimal realized = decimal(row, "realized");
                if (realized == null) {
                    realized = BigDecimal.ZERO;
                }
                int trades = row.path("closing_trades").asInt(row.path("closingTrades").asInt(0));
                accounts.add(new FinanceTaxDeskRhAccountRealizedDto(
                        suffix, label, realized.setScale(2, RoundingMode.HALF_UP), trades));
            }
        }
        if (!hasIndividual(accounts)) {
            return Optional.empty();
        }
        BigDecimal total = decimal(root, "total_realized");
        if (total == null) {
            total = accounts.stream()
                    .map(FinanceTaxDeskRhAccountRealizedDto::realized)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return Optional.of(new Fetched(
                start, asOf, total.setScale(2, RoundingMode.HALF_UP), List.copyOf(accounts), List.copyOf(warnings)));
    }

    static boolean hasIndividual(List<FinanceTaxDeskRhAccountRealizedDto> accounts) {
        if (accounts == null) {
            return false;
        }
        return accounts.stream().anyMatch(a -> INDIVIDUAL_SUFFIX.equals(a.suffix()));
    }

    private String writeAccounts(List<FinanceTaxDeskRhAccountRealizedDto> accounts) {
        try {
            return objectMapper.writeValueAsString(accounts == null ? List.of() : accounts);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store Robinhood realized accounts", e);
        }
    }

    private List<FinanceTaxDeskRhAccountRealizedDto> readAccounts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<FinanceTaxDeskRhAccountRealizedDto> out = new ArrayList<>();
            for (JsonNode row : node) {
                String suffix = text(row, "suffix");
                if (suffix == null || suffix.isBlank()) {
                    continue;
                }
                String label = text(row, "label");
                BigDecimal realized = decimal(row, "realized");
                int trades = row.path("closingTrades").asInt(row.path("closing_trades").asInt(0));
                out.add(new FinanceTaxDeskRhAccountRealizedDto(
                        suffix,
                        label == null || label.isBlank() ? suffix : label,
                        (realized == null ? BigDecimal.ZERO : realized).setScale(2, RoundingMode.HALF_UP),
                        trades));
            }
            return out;
        } catch (Exception e) {
            log.warn("Could not read stored Robinhood realized accounts: {}", e.toString());
            return List.of();
        }
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return v.asText().trim();
            }
            if (v != null && v.isNumber()) {
                return v.asText();
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual()) {
            String t = v.asText().trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
