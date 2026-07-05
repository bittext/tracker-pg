package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "robinhood_crypto_trading_settings")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodCryptoTradingSettings {

    @Id
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "auto_trade_enabled", nullable = false)
    private boolean autoTradeEnabled;

    @Column(name = "auto_trade_kill_switch", nullable = false)
    private boolean autoTradeKillSwitch;

    @Column(name = "auto_trade_min_positivity_buy", nullable = false, precision = 6, scale = 2)
    private BigDecimal autoTradeMinPositivityBuy = new BigDecimal("15.00");

    @Column(name = "auto_trade_max_positivity_sell", nullable = false, precision = 6, scale = 2)
    private BigDecimal autoTradeMaxPositivitySell = new BigDecimal("-15.00");

    @Column(name = "auto_trade_min_spike_z", nullable = false, precision = 8, scale = 4)
    private BigDecimal autoTradeMinSpikeZ = new BigDecimal("1.5000");

    @Column(name = "auto_trade_min_mentions_24h", nullable = false)
    private int autoTradeMinMentions24h = 5;

    @Column(name = "auto_trade_order_quote_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal autoTradeOrderQuoteAmount = new BigDecimal("25.00");

    @Column(name = "auto_trade_max_trades_per_day", nullable = false)
    private int autoTradeMaxTradesPerDay = 3;

    @Column(name = "auto_trade_max_daily_notional", precision = 19, scale = 2)
    private BigDecimal autoTradeMaxDailyNotional = new BigDecimal("500.00");

    @Column(name = "auto_trade_cooldown_minutes", nullable = false)
    private int autoTradeCooldownMinutes = 60;

    @Column(name = "allowed_symbols_json", nullable = false, columnDefinition = "TEXT")
    private String allowedSymbolsJson = "[\"BTC\",\"ETH\"]";

    @Column(name = "auto_trade_last_run_at")
    private Instant autoTradeLastRunAt;

    @Column(name = "auto_trade_last_run_message", columnDefinition = "TEXT")
    private String autoTradeLastRunMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
