package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.CompanyFinancialsResponseDto;
import com.svp.tracker.finance.dto.CompanyFinancialsTrendDto;
import com.svp.tracker.finance.dto.CompanyResearchFundamentalsDto;
import com.svp.tracker.finance.dto.SymbolSearchMatchDto;
import com.svp.tracker.finance.dto.SymbolSearchResponseDto;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
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

    private final AlphaVantageFinancialsService alphaVantageFinancialsService;
    private final AlphaVantageOverviewService alphaVantageOverviewService;
    private final SymbolSearchService symbolSearchService;
    private final CompanyFinancialsTrendService trendService;

    public CompanyFinancialsResponseDto quarterlyFinancials(String symbolRaw) {
        String symbol = symbolRaw == null ? "" : symbolRaw.trim().toUpperCase(Locale.ROOT);
        if (symbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol required");
        }

        AlphaVantageFinancialsService.Result result = alphaVantageFinancialsService.quarterlyFinancials(symbol);
        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Financials temporarily unavailable (rate limited or no API key configured) — try again later.");
        }

        CompanyFinancialsTrendDto trend = trendService.assess(result.quarters());

        String companyName = null;
        try {
            CompanyResearchFundamentalsDto overview = alphaVantageOverviewService.overview(symbol);
            if (overview != null) {
                companyName = overview.name();
            }
        } catch (Exception ignored) {
            // Company name is a nicety; quarterly data/trend still stands without it.
        }

        return new CompanyFinancialsResponseDto(
                symbol, companyName, result.quarters(), trend, "alpha-vantage", Instant.now().toString());
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
}
