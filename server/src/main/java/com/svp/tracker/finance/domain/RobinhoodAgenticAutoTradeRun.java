package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "robinhood_agentic_auto_trade_runs")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticAutoTradeRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "tickers_evaluated", nullable = false)
    private int tickersEvaluated;

    @Column(name = "signals_generated", nullable = false)
    private int signalsGenerated;

    @Column(name = "orders_reviewed", nullable = false)
    private int ordersReviewed;

    @Column(name = "orders_placed", nullable = false)
    private int ordersPlaced;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
}
