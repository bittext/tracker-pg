package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.CompanyFinancialsResponseDto;
import com.svp.tracker.finance.dto.CompanyFinancialsSearchHistoryItemDto;
import com.svp.tracker.finance.dto.SymbolSearchResponseDto;
import com.svp.tracker.finance.service.CompanyFinancialsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Markets Research → Financials: ad-hoc quarterly financials + trend read for any symbol. No
 * watchlist dependency (unlike company-research), so any authenticated user can look up any
 * company.
 */
@RestController
@RequestMapping("/api/markets/company-financials")
@RequiredArgsConstructor
@Slf4j
public class CompanyFinancialsController {

    private final CompanyFinancialsService companyFinancialsService;

    @GetMapping("/quarters")
    public CompanyFinancialsResponseDto quarters(@RequestParam String symbol) {
        return companyFinancialsService.quarterlyFinancials(symbol);
    }

    @GetMapping("/symbol-search")
    public SymbolSearchResponseDto symbolSearch(@RequestParam String keywords) {
        return companyFinancialsService.searchSymbol(keywords);
    }

    @GetMapping("/recent")
    public List<CompanyFinancialsSearchHistoryItemDto> recent() {
        return companyFinancialsService.recentSearches();
    }

    @DeleteMapping("/recent/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecent(@PathVariable long id) {
        companyFinancialsService.deleteRecentSearch(id);
    }
}
