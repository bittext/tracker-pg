package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression(
        "'${tracker.finance.robinhood-agentic.auto-trade.enabled-config:false}'.trim().equalsIgnoreCase('true')")
public class RobinhoodAgenticAutoTradeScheduler {

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticAutoTradeProperties autoTradeProps;
    private final RobinhoodAgenticAutoTradeService autoTradeService;

    /** Invoked by admin cron scheduler ({@code finance.robinhood-agentic.auto-trade}). */
    public void pollAutoTrade() {
        if (!agenticProps.enabled() || !agenticProps.executionEnabled() || !autoTradeProps.enabled()) {
            return;
        }
        log.debug("Robinhood Agentic auto-trade poll starting");
        autoTradeService.evaluateAllScheduled();
    }
}
