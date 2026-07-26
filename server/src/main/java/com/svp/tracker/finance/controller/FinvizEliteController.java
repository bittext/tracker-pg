package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.finviz.FinvizElitePresetDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteStatusDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteTableDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteWatchRequestDto;
import com.svp.tracker.finance.dto.finviz.FinvizEliteWatchResultDto;
import com.svp.tracker.finance.service.FinvizEliteService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/markets/finviz", "/api/finance/finviz"})
@RequiredArgsConstructor
public class FinvizEliteController {

    private final FinvizEliteService service;

    @GetMapping("/status")
    public FinvizEliteStatusDto status() {
        return service.status();
    }

    @GetMapping("/presets")
    public List<FinvizElitePresetDto> presets() {
        return service.presets();
    }

    @GetMapping("/screener")
    public FinvizEliteTableDto screener(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean force) {
        if (url != null && !url.isBlank()) {
            return service.runScreenerUrl(url, limit, force);
        }
        if (preset != null && !preset.isBlank()) {
            return service.runPreset(preset, limit, force);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide preset= or url=");
    }

    @GetMapping("/signals/{name}")
    public FinvizEliteTableDto signal(
            @PathVariable String name,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean force) {
        return service.runSignal(name, limit, force);
    }

    @GetMapping("/groups")
    public FinvizEliteTableDto groups(
            @RequestParam(defaultValue = "sector") String groupBy,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean force) {
        return service.groups(groupBy, limit, force);
    }

    @GetMapping("/news")
    public FinvizEliteTableDto news(
            @RequestParam(required = false) Integer limit, @RequestParam(defaultValue = "false") boolean force) {
        return service.news(limit, force);
    }

    @GetMapping("/options")
    public FinvizEliteTableDto options(
            @RequestParam("t") String symbol,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean force) {
        return service.options(symbol, limit, force);
    }

    @GetMapping("/portfolio")
    public FinvizEliteTableDto portfolio(
            @RequestParam(required = false) Integer limit, @RequestParam(defaultValue = "false") boolean force) {
        return service.portfolio(limit, force);
    }

    @PostMapping("/watch")
    @ResponseStatus(HttpStatus.OK)
    public FinvizEliteWatchResultDto watch(@RequestBody FinvizEliteWatchRequestDto body) {
        return service.addToWatch(body);
    }
}
