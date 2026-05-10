package com.svp.tracker.finance.controller;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.BreakoutCandidatesDto;
import com.svp.tracker.finance.dto.FinanceCrawlSnapshotDto;
import com.svp.tracker.finance.dto.MarketOverviewDto;
import com.svp.tracker.finance.dto.RobinhoodCsvDirectoryImportDto;
import com.svp.tracker.finance.dto.RobinhoodCsvImportResultDto;
import com.svp.tracker.finance.dto.RobinhoodCsvSavedImportDto;
import com.svp.tracker.finance.dto.RobinhoodCsvUploadStatusDto;
import com.svp.tracker.finance.dto.RobinhoodStocksSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodTransactionsDto;
import com.svp.tracker.finance.dto.StockNewsDto;
import com.svp.tracker.finance.dto.Surge52WeekHighsDto;
import com.svp.tracker.finance.service.BreakoutScanService;
import com.svp.tracker.finance.service.FinanceCrawlService;
import com.svp.tracker.finance.service.MarketOverviewService;
import com.svp.tracker.finance.service.RobinhoodCsvImportService;
import com.svp.tracker.finance.service.RobinhoodFinanceService;
import com.svp.tracker.finance.service.StockNewsService;
import com.svp.tracker.finance.service.Surge52WeekHighsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/finance/robinhood")
@RequiredArgsConstructor
@Slf4j
public class FinanceController {

    private final RobinhoodFinanceService robinhoodFinanceService;
    private final RobinhoodCsvImportService robinhoodCsvImportService;
    private final StockNewsService stockNewsService;
    private final Surge52WeekHighsService surge52WeekHighsService;
    private final FinanceCrawlService financeCrawlService;
    private final BreakoutScanService breakoutScanService;
    private final MarketOverviewService marketOverviewService;
    private final FinanceProperties financeProperties;

    /**
     * Rows from the configured table, optional {@code year} / {@code month} filter on {@code
     * tracker.finance.transaction-date-column}, capped.
     */
    @GetMapping("/transactions")
    public RobinhoodTransactionsDto transactions(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "symbol", required = false) String symbol) {
        validateQuery(year, month);
        log.info("GET /api/finance/robinhood/transactions year={} month={} symbol={}", year, month, symbol);
        try {
            return robinhoodFinanceService.fetchTransactions(year, month, symbol);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** Distinct values from {@code tracker.finance.stock-symbol-column} (e.g. instrument), ordered, capped. */
    @GetMapping("/symbols")
    public List<String> stockSymbols() {
        log.info("GET /api/finance/robinhood/symbols");
        try {
            return robinhoodFinanceService.fetchDistinctStockSymbols();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Buy/sell rollups by instrument + contract (description) for a calendar year (financial year), optional
     * instrument filter. Skips non-trade codes (ACH, etc.); matches BTO/Buy and STC/Sell.
     */
    @GetMapping("/stocks-summary")
    public RobinhoodStocksSummaryDto stocksSummary(
            @RequestParam(name = "year") int year,
            @RequestParam(name = "symbol", required = false) String symbol) {
        validateYear(year);
        if (financeProperties.transactionDateColumn().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Configure tracker.finance.transaction-date-column for stocks summary");
        }
        log.info("GET /api/finance/robinhood/stocks-summary year={} symbol={}", year, symbol);
        try {
            return robinhoodFinanceService.fetchStocksSummary(year, symbol);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Local-only Robinhood CSV import (no credential handling). Default is dry-run; pass {@code apply=true} to insert
     * into {@code tracker.finance.robinhood-table}.
     */
    @PostMapping("/import-csv")
    public RobinhoodCsvImportResultDto importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "apply", required = false, defaultValue = "false") boolean apply) {
        log.info(
                "POST /api/finance/robinhood/import-csv apply={} filename={} size={}",
                apply,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);
        try {
            return robinhoodCsvImportService.importCsv(file, apply);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Imports every {@code *.csv} in {@code tracker.finance.robinhood-csv-import-directory}. With {@code apply=true},
     * files that finish with no row-level errors are moved to {@code tracker.finance.robinhood-csv-uploaded-directory}.
     */
    @PostMapping("/import-csv-directory")
    public RobinhoodCsvDirectoryImportDto importCsvDirectory(
            @RequestParam(name = "apply", required = false, defaultValue = "false") boolean apply) {
        log.info("POST /api/finance/robinhood/import-csv-directory apply={}", apply);
        try {
            return robinhoodCsvImportService.importAllCsvFromConfiguredDirectory(apply);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Whether {@code tracker.finance.robinhood-csv-import-directory} is set (upload UI). {@code importDirectory} is the
     * normalized path when configured.
     */
    @GetMapping("/csv-import-upload-status")
    public RobinhoodCsvUploadStatusDto csvImportUploadStatus() {
        String raw = financeProperties.robinhoodCsvImportDirectory().trim();
        if (raw.isBlank()) {
            return new RobinhoodCsvUploadStatusDto(false, "");
        }
        try {
            return new RobinhoodCsvUploadStatusDto(true, Path.of(raw).toAbsolutePath().normalize().toString());
        } catch (Exception e) {
            return new RobinhoodCsvUploadStatusDto(true, raw);
        }
    }

    /**
     * Saves the uploaded CSV into {@code tracker.finance.robinhood-csv-import-directory}, then runs the same import as
     * {@link #importCsv}. On Ubuntu/Lightsail the directory is often mounted from {@code /home/ubuntu/robinhood/reports/import}.
     */
    @PostMapping("/import-csv-save-to-directory")
    public RobinhoodCsvSavedImportDto importCsvSaveToDirectory(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "apply", required = false, defaultValue = "false") boolean apply) {
        log.info(
                "POST /api/finance/robinhood/import-csv-save-to-directory apply={} filename={} size={}",
                apply,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);
        try {
            return robinhoodCsvImportService.saveUploadToImportDirectoryAndImport(file, apply);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** Latest trusted finance headlines for a monitored stock symbol/instrument. */
    @GetMapping("/news")
    public StockNewsDto stockNews(
            @RequestParam(name = "symbol", required = false) String symbol,
            @RequestParam(name = "companyName", required = false) String companyName,
            @RequestParam(name = "limit", required = false) Integer limit) {
        log.info("GET /api/finance/robinhood/news symbol={} companyName={} limit={}", symbol, companyName, limit);
        try {
            return stockNewsService.fetchLatestNews(symbol, companyName, limit);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Single payload for the Finance “Crawler” tab: two topic headline blocks, a deeper symbol crawl for a fixed
     * watchlist, and major-index marks (SPY, QQQ, DIA, IWM) for session context.
     */
    @GetMapping("/crawl-snapshot")
    public FinanceCrawlSnapshotDto crawlSnapshot() {
        log.info("GET /api/finance/robinhood/crawl-snapshot");
        try {
            return financeCrawlService.buildSnapshot();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
    }

    /** Symbols at the 52-week high (Yahoo quote), after scanning multiple Yahoo predefined screeners. */
    @GetMapping("/rising-52w-highs")
    public Surge52WeekHighsDto rising52WeekHighs(@RequestParam(name = "limit", required = false) Integer limit) {
        log.info("GET /api/finance/robinhood/rising-52w-highs limit={}", limit);
        try {
            return surge52WeekHighsService.fetchRecent52WeekHighRisers(limit);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Trading screeners: NASDAQ-listed names in a conventional USD mid-cap band (~$2B–$10B) from merged Yahoo screeners +
     * quote filters. Not investment advice.
     */
    @GetMapping("/nasdaq-mid-cap-screener")
    public Surge52WeekHighsDto nasdaqMidCapScreener(@RequestParam(name = "limit", required = false) Integer limit) {
        log.info("GET /api/finance/robinhood/nasdaq-mid-cap-screener limit={}", limit);
        try {
            return surge52WeekHighsService.fetchNasdaqMidCapScreen(limit);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Heuristic scan for stocks showing a technical “breakout setup” (resistance proximity, volume vs baseline, ATR
     * contraction, trend). Yahoo screeners + daily chart; not investment advice.
     */
    @GetMapping("/breakout-candidates")
    public BreakoutCandidatesDto breakoutCandidates(@RequestParam(name = "limit", required = false) Integer limit) {
        log.info("GET /api/finance/robinhood/breakout-candidates limit={}", limit);
        try {
            return breakoutScanService.scan(limit);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Finance → Market tab: global + US futures, exchange composites, headline indexes, with day / MTD / YTD context
     * from Yahoo Finance (can be delayed). Not investment advice.
     */
    @GetMapping("/market-overview")
    public MarketOverviewDto marketOverview() {
        log.info("GET /api/finance/robinhood/market-overview");
        try {
            return marketOverviewService.load();
        } catch (Exception e) {
            log.warn("market-overview failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not load market overview", e);
        }
    }

    private static void validateYear(int year) {
        if (year < 1900 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year must be between 1900 and 2100");
        }
    }

    private void validateQuery(Integer year, Integer month) {
        if (month != null && year == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year is required when month is set");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be 1–12");
        }
        if (year != null && (year < 1900 || year > 2100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year must be between 1900 and 2100");
        }
        if (year != null && financeProperties.transactionDateColumn().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Configure tracker.finance.transaction-date-column (and transaction-date-quoted if using \"name\") "
                            + "to filter by year or month");
        }
    }
}
