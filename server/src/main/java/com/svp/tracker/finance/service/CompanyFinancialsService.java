package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.FinanceCompanyFinancialsSearch;
import com.svp.tracker.finance.dto.CompanyFinancialsResponseDto;
import com.svp.tracker.finance.dto.CompanyFinancialsSearchHistoryItemDto;
import com.svp.tracker.finance.dto.CompanyFinancialsTrendDto;
import com.svp.tracker.finance.dto.CompanyResearchFundamentalsDto;
import com.svp.tracker.finance.dto.SymbolSearchMatchDto;
import com.svp.tracker.finance.dto.SymbolSearchResponseDto;
import com.svp.tracker.finance.repository.FinanceCompanyFinancialsSearchRepository;
import java.time.Instant;
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

    private final AlphaVantageFinancialsService alphaVantageFinancialsService;
    private final AlphaVantageOverviewService alphaVantageOverviewService;
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
        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Financials temporarily unavailable (rate limited or no API key configured) — try again later.");
        }

        CompanyFinancialsTrendDto trend = trendService.assess(result.quarters());

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
                symbol, companyName, result.quarters(), trend, "alpha-vantage", Instant.now().toString());
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
