package com.svp.tracker.finance.predicts.controller;

import com.svp.tracker.finance.predicts.dto.PredictsLeaderboardDto;
import com.svp.tracker.finance.predicts.dto.PredictsMentionDto;
import com.svp.tracker.finance.predicts.dto.PredictsSourceHealthDto;
import com.svp.tracker.finance.predicts.dto.PredictsSymbolSummaryDto;
import com.svp.tracker.finance.predicts.dto.PredictsTickerDto;
import com.svp.tracker.finance.predicts.dto.PredictsTickerWriteRequest;
import com.svp.tracker.finance.predicts.dto.PredictsTimeseriesDto;
import com.svp.tracker.finance.predicts.service.PredictsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finance → Trading → Predicts REST surface. Member-scoped: ticker CRUD is per-user; read-side queries
 * (summary, timeseries, leaderboard, mentions, sources) are global because the underlying community
 * data isn't owned by any one user — it's normalized social chatter for each ticker.
 */
@RestController
@RequestMapping("/api/finance/predicts")
@RequiredArgsConstructor
@Slf4j
public class PredictsController {

    private final PredictsService predictsService;

    @GetMapping("/tickers")
    public List<PredictsTickerDto> listTickers() {
        return predictsService.listMyTickers();
    }

    @PostMapping("/tickers")
    public PredictsTickerDto addTicker(@RequestBody PredictsTickerWriteRequest req) {
        log.info("POST /api/finance/predicts/tickers symbol={}", req == null ? null : req.symbol());
        return predictsService.addTicker(req);
    }

    @PutMapping("/tickers/{id}")
    public PredictsTickerDto updateTicker(@PathVariable long id, @RequestBody PredictsTickerWriteRequest req) {
        return predictsService.updateTicker(id, req);
    }

    @DeleteMapping("/tickers/{id}")
    public void deleteTicker(@PathVariable long id) {
        predictsService.deleteTicker(id);
    }

    @GetMapping("/sources")
    public List<PredictsSourceHealthDto> sources() {
        return predictsService.listSourceHealth();
    }

    @GetMapping("/leaderboard")
    public PredictsLeaderboardDto leaderboard(
            @RequestParam(name = "type", required = false, defaultValue = "hot") String type,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return predictsService.leaderboard(type, limit);
    }

    @GetMapping("/{symbol}/summary")
    public PredictsSymbolSummaryDto summary(@PathVariable String symbol) {
        return predictsService.summary(symbol);
    }

    @GetMapping("/{symbol}/timeseries")
    public PredictsTimeseriesDto timeseries(
            @PathVariable String symbol,
            @RequestParam(name = "window", required = false, defaultValue = "1h") String window,
            @RequestParam(name = "source", required = false, defaultValue = "all") String source,
            @RequestParam(name = "days", required = false, defaultValue = "7") int days) {
        return predictsService.timeseries(symbol, window, source, days);
    }

    @GetMapping("/{symbol}/mentions")
    public List<PredictsMentionDto> mentions(
            @PathVariable String symbol, @RequestParam(name = "limit", required = false) Integer limit) {
        return predictsService.recentMentions(symbol, limit);
    }
}
