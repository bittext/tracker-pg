package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "robinhood_agentic_settings")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval = true;

    @Column(name = "max_order_notional", precision = 19, scale = 2)
    private BigDecimal maxOrderNotional;

    @Column(name = "allowed_symbols", columnDefinition = "TEXT")
    private String allowedSymbols;

    @Column(name = "auto_trade_enabled", nullable = false)
    private boolean autoTradeEnabled;

    @Column(name = "auto_trade_kill_switch", nullable = false)
    private boolean autoTradeKillSwitch;

    @Column(name = "auto_trade_require_approval", nullable = false)
    private boolean autoTradeRequireApproval = true;

    @Column(name = "auto_trade_min_positivity_buy", nullable = false, precision = 6, scale = 2)
    private BigDecimal autoTradeMinPositivityBuy = new BigDecimal("15.00");

    @Column(name = "auto_trade_max_positivity_sell", nullable = false, precision = 6, scale = 2)
    private BigDecimal autoTradeMaxPositivitySell = new BigDecimal("-15.00");

    @Column(name = "auto_trade_min_spike_z", nullable = false, precision = 8, scale = 4)
    private BigDecimal autoTradeMinSpikeZ = new BigDecimal("1.5000");

    @Column(name = "auto_trade_min_mentions_24h", nullable = false)
    private int autoTradeMinMentions24h = 5;

    @Column(name = "auto_trade_order_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal autoTradeOrderQuantity = BigDecimal.ONE;

    @Column(name = "auto_trade_max_trades_per_day", nullable = false)
    private int autoTradeMaxTradesPerDay = 3;

    @Column(name = "auto_trade_max_daily_notional", precision = 19, scale = 2)
    private BigDecimal autoTradeMaxDailyNotional;

    @Column(name = "auto_trade_cooldown_minutes", nullable = false)
    private int autoTradeCooldownMinutes = 60;

    @Column(name = "auto_trade_market_hours_only", nullable = false)
    private boolean autoTradeMarketHoursOnly = true;

    @Column(name = "auto_trade_last_run_at")
    private Instant autoTradeLastRunAt;

    @Column(name = "auto_trade_last_run_message", columnDefinition = "TEXT")
    private String autoTradeLastRunMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
