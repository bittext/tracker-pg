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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
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
public class CompanyFinancialsService {

    private static final int RECENT_SEARCH_LIMIT = 20;
    /** Report date is typically a few weeks after fiscal quarter end. */
    private static final int EPS_REPORT_AFTER_FISCAL_MAX_DAYS = 100;

    private final AlphaVantageFinancialsService alphaVantageFinancialsService;
    private final AlphaVantageOverviewService alphaVantageOverviewService;
    private final RobinhoodEarningsService robinhoodEarningsService;
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

        AlphaVantageFinancialsService.Result result = alphaVantageFinancialsService.quarterlyFinancials(symbol);
        List<EarningsRow> rhEarnings = robinhoodEarningsService.earnings(symbol);
        List<CompanyFinancialsQuarterDto> quarters =
                result != null ? result.quarters() : new ArrayList<>();
        MergeEps merge = applyRobinhoodEarnings(quarters, rhEarnings);
        quarters = merge.quarters();
        if (quarters.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Financials temporarily unavailable (rate limited or no API key configured) — try again later.");
        }

        CompanyFinancialsTrendDto trend = trendService.assess(quarters);
        if (result != null) {
            for (String w : result.warnings()) {
                if (merge.filledAny() && w.toLowerCase(Locale.ROOT).contains("eps")) {
                    continue;
                }
                trend = withWarning(trend, w);
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
        if (isFinancialSector(sector)) {
            trend = withWarning(
                    trend,
                    "This company is in a financial-services sector (banks/brokers/insurers). Alpha "
                            + "Vantage's income-statement mapping is known to misreport revenue for this "
                            + "sector (e.g. \"net revenue\" vs \"total revenue\" tags) — cross-check revenue "
                            + "figures against the company's own investor-relations release.");
        }

        return new CompanyFinancialsResponseDto(
                symbol, companyName, quarters, trend, merge.sourceLabel(result != null), Instant.now().toString());
    }

    private record MergeEps(List<CompanyFinancialsQuarterDto> quarters, boolean filledAny) {
        String sourceLabel(boolean hadAlpha) {
            if (filledAny && hadAlpha) {
                return "alpha-vantage + robinhood-earnings";
            }
            if (filledAny) {
                return "robinhood-earnings";
            }
            return "alpha-vantage";
        }
    }

    /**
     * Overlay Robinhood EPS actual/estimate onto Alpha Vantage quarters (match report date to the
     * fiscal end that precedes it). If Alpha Vantage returned no rows, build EPS-only quarters from
     * Robinhood.
     */
    static MergeEps applyRobinhoodEarnings(
            List<CompanyFinancialsQuarterDto> quarters, List<EarningsRow> rhEarnings) {
        if (rhEarnings == null || rhEarnings.isEmpty()) {
            return new MergeEps(quarters, false);
        }
        if (quarters == null || quarters.isEmpty()) {
            List<CompanyFinancialsQuarterDto> fromRh = new ArrayList<>();
            for (EarningsRow row : rhEarnings) {
                if (row.reportDate() == null || (row.epsActual() == null && row.epsEstimate() == null)) {
                    continue;
                }
                fromRh.add(quarterFromRobinhood(row));
            }
            fromRh.sort((a, b) -> a.fiscalDateEnding().compareTo(b.fiscalDateEnding()));
            return new MergeEps(fromRh, !fromRh.isEmpty());
        }
        boolean filled = false;
        List<CompanyFinancialsQuarterDto> out = new ArrayList<>(quarters.size());
        for (CompanyFinancialsQuarterDto q : quarters) {
            EarningsRow match = matchRobinhoodRow(q.fiscalDateEnding(), rhEarnings);
            if (match == null) {
                out.add(q);
                continue;
            }
            Double actual = match.epsActual() != null ? match.epsActual() : q.epsActual();
            Double estimate = match.epsEstimate() != null ? match.epsEstimate() : q.epsEstimate();
            Double surprise = (actual != null && estimate != null && estimate != 0)
                    ? ((actual - estimate) / Math.abs(estimate)) * 100
                    : q.epsSurprisePct();
            boolean changed = !eq(actual, q.epsActual()) || !eq(estimate, q.epsEstimate());
            filled = filled || changed;
            out.add(new CompanyFinancialsQuarterDto(
                    q.fiscalDateEnding(),
                    q.revenue(),
                    q.netIncome(),
                    q.grossProfit(),
                    q.operatingIncome(),
                    q.netMarginPct(),
                    actual,
                    estimate,
                    surprise));
        }
        return new MergeEps(out, filled);
    }

    private static CompanyFinancialsQuarterDto quarterFromRobinhood(EarningsRow row) {
        Double surprise = (row.epsActual() != null && row.epsEstimate() != null && row.epsEstimate() != 0)
                ? ((row.epsActual() - row.epsEstimate()) / Math.abs(row.epsEstimate())) * 100
                : null;
        // Report date is after quarter end; back up ~5 weeks so the column stays a fiscal-ish date.
        LocalDate fiscal = row.reportDate().minusDays(35);
        return new CompanyFinancialsQuarterDto(
                fiscal.toString(),
                null,
                null,
                null,
                null,
                null,
                row.epsActual(),
                row.epsEstimate(),
                surprise);
    }

    private static EarningsRow matchRobinhoodRow(String fiscalDateEnding, List<EarningsRow> rows) {
        LocalDate fiscal;
        try {
            fiscal = LocalDate.parse(fiscalDateEnding);
        } catch (Exception e) {
            return null;
        }
        EarningsRow best = null;
        long bestDays = Long.MAX_VALUE;
        for (EarningsRow row : rows) {
            if (row.reportDate() == null) {
                continue;
            }
            if (row.reportDate().isBefore(fiscal)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(fiscal, row.reportDate());
            if (days >= 0 && days <= EPS_REPORT_AFTER_FISCAL_MAX_DAYS && days < bestDays) {
                bestDays = days;
                best = row;
            }
        }
        return best;
    }

    private static boolean eq(Double a, Double b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Double.compare(a, b) == 0;
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
