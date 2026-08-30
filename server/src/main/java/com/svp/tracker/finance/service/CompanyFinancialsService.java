package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceCompanyFinancialsSearch;
import com.svp.tracker.finance.dto.CompanyFinancialsQuarterDto;
import com.svp.tracker.finance.dto.CompanyFinancialsResponseDto;
import com.svp.tracker.finance.dto.CompanyFinancialsSearchHistoryItemDto;
import com.svp.tracker.finance.dto.CompanyFinancialsTrendDto;
import com.svp.tracker.finance.dto.CompanyResearchFundamentalsDto;
import com.svp.tracker.finance.dto.SymbolSearchMatchDto;
import com.svp.tracker.finance.dto.SymbolSearchResponseDto;
import com.svp.tracker.finance.repository.FinanceCompanyFinancialsSearchRepository;
import com.svp.tracker.finance.service.RobinhoodEarningsService.EarningsRow;
import com.svp.tracker.finance.service.RobinhoodFinancialsService.FinancialsRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Markets → Research → Financials: quarterly financials + trend read for an ad-hoc symbol. No
 * watchlist dependency — any authenticated user can look up any symbol.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyFinancialsService {

    private static final int RECENT_SEARCH_LIMIT = 20;
    /** Report date is typically a few weeks after fiscal quarter end. */
    private static final int EPS_REPORT_AFTER_FISCAL_MAX_DAYS = 100;

    private final AlphaVantageFinancialsService alphaVantageFinancialsService;
    private final AlphaVantageOverviewService alphaVantageOverviewService;
    private final RobinhoodEarningsService robinhoodEarningsService;
    private final RobinhoodFinancialsService robinhoodFinancialsService;
    private final SymbolSearchService symbolSearchService;
    private final CompanyFinancialsTrendService trendService;
    private final FinanceCompanyFinancialsSearchRepository searchHistoryRepository;
    private final CurrentUserService currentUser;

    public CompanyFinancialsResponseDto quarterlyFinancials(String symbolRaw) {
        String symbol = symbolRaw == null ? "" : symbolRaw.trim().toUpperCase(Locale.ROOT);
        if (symbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol required");
        }

        recordSearch(symbol, null);

        try {
            return buildQuarterlyFinancials(symbol);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Company financials failed for {}", symbol, e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Financials temporarily unavailable. Try again shortly.");
        }
    }

    private CompanyFinancialsResponseDto buildQuarterlyFinancials(String symbol) {
        List<EarningsRow> rhEarnings;
        try {
            rhEarnings = robinhoodEarningsService.earnings(symbol);
        } catch (Exception e) {
            log.warn("Robinhood earnings failed for {}: {}", symbol, e.toString());
            rhEarnings = List.of();
        }

        AlphaVantageFinancialsService.Result result = null;
        try {
            result = alphaVantageFinancialsService.quarterlyFinancials(symbol);
        } catch (Exception e) {
            log.warn("Alpha Vantage financials failed for {}: {}", symbol, e.toString());
        }

        List<FinancialsRow> rhFinancials;
        try {
            rhFinancials = robinhoodFinancialsService.quarterlyFinancials(symbol);
        } catch (Exception e) {
            log.warn("Robinhood get_financials failed for {}: {}", symbol, e.toString());
            rhFinancials = List.of();
        }

        List<CompanyFinancialsQuarterDto> avQuarters =
                result != null && result.quarters() != null ? result.quarters() : List.of();
        MergeEps merge = applyRobinhoodPrimary(avQuarters, rhEarnings, rhFinancials);
        List<CompanyFinancialsQuarterDto> quarters = merge.quarters();
        if (quarters.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Financials temporarily unavailable (Robinhood earnings and Alpha Vantage both empty).");
        }

        CompanyFinancialsTrendDto trend = trendService.assess(quarters);
        if (result != null && result.warnings() != null && !merge.usedRobinhood()) {
            for (String w : result.warnings()) {
                if (w != null && !w.isBlank()) {
                    trend = withWarning(trend, w);
                }
            }
        }

        String companyName = null;
        String sector = null;
        try {
            CompanyResearchFundamentalsDto overview = alphaVantageOverviewService.overview(symbol);
            if (overview != null) {
                companyName = overview.name();
                sector = overview.sector();
            }
        } catch (Exception ignored) {
            // Company name/sector are a nicety; quarterly data/trend still stand without them.
        }
        if (companyName != null && !companyName.isBlank()) {
            recordSearch(symbol, companyName);
        }
        if (isFinancialSector(sector) && !merge.usedFinancials()) {
            trend = withWarning(
                    trend,
                    "This company is in a financial-services sector (banks/brokers/insurers). Alpha "
                            + "Vantage's income-statement mapping is known to misreport revenue for this "
                            + "sector (e.g. \"net revenue\" vs \"total revenue\" tags) — cross-check revenue "
                            + "figures against the company's own investor-relations release.");
        }

        return new CompanyFinancialsResponseDto(
                symbol, companyName, quarters, trend, merge.sourceLabel(), Instant.now().toString());
    }

    record MergeEps(
            List<CompanyFinancialsQuarterDto> quarters,
            boolean usedRobinhood,
            boolean usedFinancials,
            boolean usedAlpha) {
        String sourceLabel() {
            List<String> parts = new ArrayList<>();
            if (usedFinancials) {
                parts.add("robinhood-financials");
            }
            if (usedRobinhood) {
                parts.add("robinhood-earnings");
            }
            if (usedAlpha || parts.isEmpty()) {
                parts.add("alpha-vantage");
            }
            return String.join(" + ", parts);
        }
    }

    static MergeEps applyRobinhoodPrimary(
            List<CompanyFinancialsQuarterDto> avQuarters, List<EarningsRow> rhEarnings) {
        return applyRobinhoodPrimary(avQuarters, rhEarnings, List.of());
    }

    /**
     * Robinhood earnings is the EPS quarter list; {@code get_financials} supplies revenue / net
     * income / margin. Alpha Vantage fills remaining gaps and older extra quarters.
     */
    static MergeEps applyRobinhoodPrimary(
            List<CompanyFinancialsQuarterDto> avQuarters,
            List<EarningsRow> rhEarnings,
            List<FinancialsRow> rhFinancials) {
        List<CompanyFinancialsQuarterDto> av = avQuarters == null ? List.of() : avQuarters;
        List<EarningsRow> rh = rhEarnings == null ? List.of() : rhEarnings;
        List<FinancialsRow> fin = rhFinancials == null ? List.of() : rhFinancials;
        if (rh.isEmpty() && fin.isEmpty()) {
            List<CompanyFinancialsQuarterDto> onlyAv = sanitizeQuarters(av);
            return new MergeEps(onlyAv, false, false, !onlyAv.isEmpty());
        }

        boolean usedAlpha = false;
        boolean usedFinancials = false;
        boolean[] avUsed = new boolean[av.size()];
        boolean[] finUsed = new boolean[fin.size()];
        List<CompanyFinancialsQuarterDto> out = new ArrayList<>();
        for (EarningsRow row : rh) {
            if (row == null
                    || (row.reportDate() == null && row.epsActual() == null && row.epsEstimate() == null)) {
                continue;
            }
            int finIdx = indexOfMatchingFinancials(row, fin, finUsed);
            FinancialsRow finRow = finIdx >= 0 ? fin.get(finIdx) : null;
            if (finIdx >= 0) {
                finUsed[finIdx] = true;
                usedFinancials = true;
            }
            int avIdx = indexOfMatchingAvQuarter(row, av, avUsed);
            CompanyFinancialsQuarterDto avq = avIdx >= 0 ? av.get(avIdx) : null;
            if (avIdx >= 0) {
                avUsed[avIdx] = true;
            }
            Double actual = row.epsActual() != null ? row.epsActual() : (avq != null ? avq.epsActual() : null);
            Double estimate = row.epsEstimate() != null ? row.epsEstimate() : (avq != null ? avq.epsEstimate() : null);
            Double revenue = firstNonNull(finRow != null ? finRow.revenue() : null, avq != null ? avq.revenue() : null);
            Double netIncome =
                    firstNonNull(finRow != null ? finRow.netIncome() : null, avq != null ? avq.netIncome() : null);
            Double grossProfit =
                    firstNonNull(finRow != null ? finRow.grossProfit() : null, avq != null ? avq.grossProfit() : null);
            Double operatingIncome = avq != null ? avq.operatingIncome() : null;
            Double netMargin = firstNonNull(
                    finRow != null ? finRow.netMarginPct() : null, avq != null ? avq.netMarginPct() : null);
            if (netMargin == null && revenue != null && netIncome != null && revenue != 0) {
                netMargin = (netIncome / revenue) * 100;
            }
            if (avq != null
                    && ((finRow == null || finRow.revenue() == null) && avq.revenue() != null
                            || (finRow == null || finRow.netIncome() == null) && avq.netIncome() != null
                            || (row.epsActual() == null && avq.epsActual() != null)
                            || (row.epsEstimate() == null && avq.epsEstimate() != null))) {
                usedAlpha = true;
            }
            Double surprise = surprisePct(actual, estimate, avq);
            String fiscal = firstNonBlank(
                    finRow != null ? finRow.periodEndDate() : null,
                    avq != null ? avq.fiscalDateEnding() : null,
                    row.reportDate() != null ? row.reportDate().minusDays(35).toString() : null);
            if (fiscal == null) {
                continue;
            }
            out.add(new CompanyFinancialsQuarterDto(
                    fiscal,
                    finite(revenue),
                    finite(netIncome),
                    finite(grossProfit),
                    finite(operatingIncome),
                    finite(netMargin),
                    finite(actual),
                    finite(estimate),
                    surprise));
        }
        for (int i = 0; i < fin.size(); i++) {
            if (finUsed[i] || fin.get(i) == null) {
                continue;
            }
            FinancialsRow leftover = fin.get(i);
            int avIdx = indexOfAvByFiscal(leftover.periodEndDate(), av, avUsed);
            CompanyFinancialsQuarterDto avq = avIdx >= 0 ? av.get(avIdx) : null;
            if (avIdx >= 0) {
                avUsed[avIdx] = true;
            }
            usedFinancials = true;
            if (avq != null && leftover.revenue() == null && avq.revenue() != null) {
                usedAlpha = true;
            }
            Double revenue = firstNonNull(leftover.revenue(), avq != null ? avq.revenue() : null);
            Double netIncome = firstNonNull(leftover.netIncome(), avq != null ? avq.netIncome() : null);
            Double netMargin = firstNonNull(leftover.netMarginPct(), avq != null ? avq.netMarginPct() : null);
            if (netMargin == null && revenue != null && netIncome != null && revenue != 0) {
                netMargin = (netIncome / revenue) * 100;
            }
            out.add(new CompanyFinancialsQuarterDto(
                    leftover.periodEndDate(),
                    finite(revenue),
                    finite(netIncome),
                    finite(firstNonNull(leftover.grossProfit(), avq != null ? avq.grossProfit() : null)),
                    finite(avq != null ? avq.operatingIncome() : null),
                    finite(netMargin),
                    finite(avq != null ? avq.epsActual() : null),
                    finite(avq != null ? avq.epsEstimate() : null),
                    finite(avq != null ? avq.epsSurprisePct() : null)));
        }
        for (int i = 0; i < av.size(); i++) {
            if (!avUsed[i] && av.get(i) != null) {
                out.add(av.get(i));
                usedAlpha = true;
            }
        }
        List<CompanyFinancialsQuarterDto> cleaned = sanitizeQuarters(out);
        return new MergeEps(cleaned, !rh.isEmpty(), usedFinancials, usedAlpha);
    }

    private static List<CompanyFinancialsQuarterDto> sanitizeQuarters(List<CompanyFinancialsQuarterDto> raw) {
        List<CompanyFinancialsQuarterDto> cleaned = new ArrayList<>();
        if (raw == null) {
            return cleaned;
        }
        for (CompanyFinancialsQuarterDto q : raw) {
            if (q == null || q.fiscalDateEnding() == null || q.fiscalDateEnding().isBlank()) {
                continue;
            }
            cleaned.add(new CompanyFinancialsQuarterDto(
                    q.fiscalDateEnding(),
                    finite(q.revenue()),
                    finite(q.netIncome()),
                    finite(q.grossProfit()),
                    finite(q.operatingIncome()),
                    finite(q.netMarginPct()),
                    finite(q.epsActual()),
                    finite(q.epsEstimate()),
                    finite(q.epsSurprisePct())));
        }
        cleaned.sort(Comparator.comparing(CompanyFinancialsQuarterDto::fiscalDateEnding));
        return cleaned;
    }

    private static Double finite(Double v) {
        return v == null || !Double.isFinite(v) ? null : v;
    }

    /** Avoids {@code double}/{@code Double} ternary unboxing (NPE when EPS actual is still null). */
    private static Double surprisePct(Double actual, Double estimate, CompanyFinancialsQuarterDto avq) {
        if (actual != null && estimate != null && estimate.doubleValue() != 0) {
            return finite(((actual - estimate) / Math.abs(estimate.doubleValue())) * 100);
        }
        return avq != null ? finite(avq.epsSurprisePct()) : null;
    }

    private static Double firstNonNull(Double a, Double b) {
        return a != null ? a : b;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static int indexOfMatchingFinancials(
            EarningsRow row, List<FinancialsRow> financials, boolean[] used) {
        if (row.year() > 0 && row.quarter() > 0) {
            for (int i = 0; i < financials.size(); i++) {
                if (used != null && i < used.length && used[i]) {
                    continue;
                }
                FinancialsRow f = financials.get(i);
                if (f != null && f.year() == row.year() && f.quarter() == row.quarter()) {
                    return i;
                }
            }
        }
        if (row.reportDate() == null) {
            return -1;
        }
        int best = -1;
        long bestDays = Long.MAX_VALUE;
        for (int i = 0; i < financials.size(); i++) {
            if (used != null && i < used.length && used[i]) {
                continue;
            }
            FinancialsRow f = financials.get(i);
            if (f == null || f.periodEndDate() == null) {
                continue;
            }
            LocalDate fiscal;
            try {
                fiscal = LocalDate.parse(f.periodEndDate());
            } catch (Exception e) {
                continue;
            }
            if (row.reportDate().isBefore(fiscal)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(fiscal, row.reportDate());
            if (days >= 0 && days <= EPS_REPORT_AFTER_FISCAL_MAX_DAYS && days < bestDays) {
                bestDays = days;
                best = i;
            }
        }
        return best;
    }

    private static int indexOfAvByFiscal(String periodEnd, List<CompanyFinancialsQuarterDto> av, boolean[] avUsed) {
        if (periodEnd == null || periodEnd.isBlank()) {
            return -1;
        }
        LocalDate target;
        try {
            target = LocalDate.parse(periodEnd);
        } catch (Exception e) {
            return -1;
        }
        int best = -1;
        long bestDays = Long.MAX_VALUE;
        for (int i = 0; i < av.size(); i++) {
            if (avUsed != null && i < avUsed.length && avUsed[i]) {
                continue;
            }
            CompanyFinancialsQuarterDto q = av.get(i);
            if (q == null || q.fiscalDateEnding() == null) {
                continue;
            }
            LocalDate fiscal;
            try {
                fiscal = LocalDate.parse(q.fiscalDateEnding());
            } catch (Exception e) {
                continue;
            }
            long days = Math.abs(ChronoUnit.DAYS.between(target, fiscal));
            if (days <= 10 && days < bestDays) {
                bestDays = days;
                best = i;
            }
        }
        return best;
    }

    private static int indexOfMatchingAvQuarter(
            EarningsRow row, List<CompanyFinancialsQuarterDto> av, boolean[] avUsed) {
        if (row.reportDate() == null) {
            return -1;
        }
        int best = -1;
        long bestDays = Long.MAX_VALUE;
        for (int i = 0; i < av.size(); i++) {
            if (avUsed != null && i < avUsed.length && avUsed[i]) {
                continue;
            }
            CompanyFinancialsQuarterDto q = av.get(i);
            if (q == null || q.fiscalDateEnding() == null) {
                continue;
            }
            LocalDate fiscal;
            try {
                fiscal = LocalDate.parse(q.fiscalDateEnding());
            } catch (Exception e) {
                continue;
            }
            if (row.reportDate().isBefore(fiscal)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(fiscal, row.reportDate());
            if (days >= 0 && days <= EPS_REPORT_AFTER_FISCAL_MAX_DAYS && days < bestDays) {
                bestDays = days;
                best = i;
            }
        }
        return best;
    }

    private static boolean isFinancialSector(String sector) {
        return sector != null && sector.toLowerCase(Locale.ROOT).contains("financ");
    }

    private static CompanyFinancialsTrendDto withWarning(CompanyFinancialsTrendDto trend, String warning) {
        List<String> warnings = new ArrayList<>(trend.warnings());
        warnings.add(warning);
        return new CompanyFinancialsTrendDto(
                trend.verdict(),
                trend.score(),
                trend.revenueTrend(),
                trend.marginTrend(),
                trend.epsTrend(),
                trend.narrative(),
                warnings);
    }

    public SymbolSearchResponseDto searchSymbol(String keywordsRaw) {
        String keywords = keywordsRaw == null ? "" : keywordsRaw.trim();
        if (keywords.isBlank()) {
            return new SymbolSearchResponseDto(keywords, List.of(), false);
        }
        List<SymbolSearchMatchDto> matches = symbolSearchService.search(keywords);
        boolean autoSelected = matches.size() == 1 && "United States".equalsIgnoreCase(matches.get(0).region());
        return new SymbolSearchResponseDto(keywords, matches, autoSelected);
    }

    public List<CompanyFinancialsSearchHistoryItemDto> recentSearches() {
        long owner = currentUser.requireUserId();
        return searchHistoryRepository
                .findByOwnerUserIdOrderBySearchedAtDesc(owner, Limit.of(RECENT_SEARCH_LIMIT))
                .stream()
                .map(r -> new CompanyFinancialsSearchHistoryItemDto(
                        r.getId(), r.getSymbol(), r.getCompanyName(), r.getSearchedAt().toString()))
                .toList();
    }

    public void deleteRecentSearch(long id) {
        long owner = currentUser.requireUserId();
        FinanceCompanyFinancialsSearch row = searchHistoryRepository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search not found"));
        searchHistoryRepository.delete(row);
    }

    /** Best-effort convenience list — a persistence hiccup here must never break the main lookup. */
    private void recordSearch(String symbol, String companyName) {
        try {
            long owner = currentUser.requireUserId();
            FinanceCompanyFinancialsSearch row = searchHistoryRepository
                    .findByOwnerUserIdAndSymbol(owner, symbol)
                    .orElseGet(FinanceCompanyFinancialsSearch::new);
            row.setOwnerUserId(owner);
            row.setSymbol(symbol);
            if (companyName != null && !companyName.isBlank()) {
                row.setCompanyName(companyName);
            }
            row.setSearchedAt(Instant.now());
            searchHistoryRepository.save(row);
        } catch (Exception e) {
            // Ignore -- recent-search convenience list, not core functionality.
        }
    }
}
