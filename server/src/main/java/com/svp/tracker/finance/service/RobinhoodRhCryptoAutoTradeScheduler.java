package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoAutoTradeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression(
        "'${tracker.finance.rh-crypto-auto-trade.enabled-config:false}'.trim().equalsIgnoreCase('true')"
                + " && !'${tracker.finance.rh-crypto-auto-trade.poll-cron:}'.trim().isEmpty()")
public class RobinhoodRhCryptoAutoTradeScheduler {

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhCryptoAutoTradeProperties autoTradeProps;
    private final RobinhoodRhCryptoAutoTradeService autoTradeService;

    /** Invoked by admin cron scheduler ({@code finance.rh-crypto-auto-trade.poll}). */
    public void pollAutoTrade() {
        if (!agenticProps.serviceConfigured() || !autoTradeProps.enabled()) {
            return;
        }
        log.debug("Robinhood Crypto auto-trade poll starting");
        autoTradeService.evaluateAllScheduled();
    }
}
