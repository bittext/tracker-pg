package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodTradeInterestDto;
import com.svp.tracker.finance.dto.RobinhoodTradeInterestRequestDto;
import com.svp.tracker.finance.service.RobinhoodTradeInterestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/finance/robinhood/trade-interests", "/api/markets/trade-interests"})
@RequiredArgsConstructor
@Slf4j
public class RobinhoodTradeInterestController {

    private final RobinhoodTradeInterestService tradeInterestService;

    @GetMapping
    public List<RobinhoodTradeInterestDto> list(@RequestParam(required = false) String status) {
        return tradeInterestService.list(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RobinhoodTradeInterestDto create(@RequestBody RobinhoodTradeInterestRequestDto body) {
        log.info(
                "POST /api/finance/robinhood/trade-interests kind={} symbol={}",
                body != null ? body.instrumentKind() : null,
                body != null ? body.symbol() : null);
        return tradeInterestService.create(body);
    }

    @PutMapping("/{id}")
    public RobinhoodTradeInterestDto update(
            @PathVariable long id, @RequestBody RobinhoodTradeInterestRequestDto body) {
        log.info("PUT /api/finance/robinhood/trade-interests/{}", id);
        return tradeInterestService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        log.info("DELETE /api/finance/robinhood/trade-interests/{}", id);
        tradeInterestService.delete(id);
    }
}
