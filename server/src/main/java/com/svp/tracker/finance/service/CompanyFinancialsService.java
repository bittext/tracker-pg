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

        List<EarningsRow> rhEarnings = robinhoodEarningsService.earnings(symbol);
        AlphaVantageFinancialsService.Result result = alphaVantageFinancialsService.quarterlyFinancials(symbol);
        List<CompanyFinancialsQuarterDto> avQuarters =
                result != null ? result.quarters() : new ArrayList<>();
        MergeEps merge = applyRobinhoodPrimary(avQuarters, rhEarnings);
        List<CompanyFinancialsQuarterDto> quarters = merge.quarters();
        if (quarters.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Financials temporarily unavailable (Robinhood earnings and Alpha Vantage both empty).");
        }

        CompanyFinancialsTrendDto trend = trendService.assess(quarters);
        if (result != null && !merge.usedRobinhood()) {
            for (String w : result.warnings()) {
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
                symbol, companyName, quarters, trend, merge.sourceLabel(), Instant.now().toString());
    }

    private record MergeEps(
            List<CompanyFinancialsQuarterDto> quarters, boolean usedRobinhood, boolean usedAlpha) {
        String sourceLabel() {
            if (usedRobinhood && usedAlpha) {
                return "robinhood-earnings + alpha-vantage";
            }
            if (usedRobinhood) {
                return "robinhood-earnings";
            }
            return "alpha-vantage";
        }
    }

    /**
     * Robinhood earnings is the primary quarter list (EPS actual/estimate). Alpha Vantage fills
     * revenue/net income and any older quarters Robinhood does not cover.
     */
    static MergeEps applyRobinhoodPrimary(
            List<CompanyFinancialsQuarterDto> avQuarters, List<EarningsRow> rhEarnings) {
        List<CompanyFinancialsQuarterDto> av = avQuarters == null ? List.of() : avQuarters;
        List<EarningsRow> rh = rhEarnings == null ? List.of() : rhEarnings;
        if (rh.isEmpty()) {
            return new MergeEps(av, false, !av.isEmpty());
        }

        boolean usedAlpha = false;
        boolean[] avUsed = new boolean[av.size()];
        List<CompanyFinancialsQuarterDto> out = new ArrayList<>();
        for (EarningsRow row : rh) {
            if (row.reportDate() == null && row.epsActual() == null && row.epsEstimate() == null) {
                continue;
            }
            int avIdx = indexOfMatchingAvQuarter(row, av);
            CompanyFinancialsQuarterDto avq = avIdx >= 0 ? av.get(avIdx) : null;
            if (avIdx >= 0) {
                avUsed[avIdx] = true;
            }
            Double actual = row.epsActual() != null ? row.epsActual() : (avq != null ? avq.epsActual() : null);
            Double estimate = row.epsEstimate() != null ? row.epsEstimate() : (avq != null ? avq.epsEstimate() : null);
            if (avq != null
                    && ((row.epsActual() == null && avq.epsActual() != null)
                            || (row.epsEstimate() == null && avq.epsEstimate() != null)
                            || avq.revenue() != null
                            || avq.netIncome() != null)) {
                usedAlpha = true;
            }
            Double surprise = (actual != null && estimate != null && estimate != 0)
                    ? ((actual - estimate) / Math.abs(estimate)) * 100
                    : (avq != null ? avq.epsSurprisePct() : null);
            String fiscal = avq != null
                    ? avq.fiscalDateEnding()
                    : (row.reportDate() != null ? row.reportDate().minusDays(35).toString() : null);
            if (fiscal == null) {
                continue;
            }
            out.add(new CompanyFinancialsQuarterDto(
                    fiscal,
                    avq != null ? avq.revenue() : null,
                    avq != null ? avq.netIncome() : null,
                    avq != null ? avq.grossProfit() : null,
                    avq != null ? avq.operatingIncome() : null,
                    avq != null ? avq.netMarginPct() : null,
                    actual,
                    estimate,
                    surprise));
        }
        for (int i = 0; i < av.size(); i++) {
            if (!avUsed[i]) {
                out.add(av.get(i));
                usedAlpha = true;
            }
        }
        out.sort((a, b) -> a.fiscalDateEnding().compareTo(b.fiscalDateEnding()));
        return new MergeEps(out, true, usedAlpha);
    }

    private static int indexOfMatchingAvQuarter(EarningsRow row, List<CompanyFinancialsQuarterDto> av) {
        if (row.reportDate() == null) {
            return -1;
        }
        int best = -1;
        long bestDays = Long.MAX_VALUE;
        for (int i = 0; i < av.size(); i++) {
            LocalDate fiscal;
            try {
                fiscal = LocalDate.parse(av.get(i).fiscalDateEnding());
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
