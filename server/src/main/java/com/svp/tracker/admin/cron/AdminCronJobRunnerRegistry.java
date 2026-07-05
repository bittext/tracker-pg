package com.svp.tracker.admin.cron;

import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoAutoTradeProperties;
import com.svp.tracker.config.RobinhoodRhCryptoTrackerProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.finance.predicts.service.PredictsBaselineService;
import com.svp.tracker.finance.predicts.service.PredictsService;
import com.svp.tracker.finance.predicts.service.RedditIngestService;
import com.svp.tracker.finance.predicts.service.StockTwitsIngestService;
import com.svp.tracker.finance.service.FinanceAlertEvaluationService;
import com.svp.tracker.finance.service.RobinhoodAgenticAutoTradeScheduler;
import com.svp.tracker.finance.service.RobinhoodAgenticSyncScheduler;
import com.svp.tracker.finance.service.RobinhoodRhCryptoAutoTradeScheduler;
import com.svp.tracker.finance.service.RobinhoodRhCryptoSnapshotScheduler;
import com.svp.tracker.finance.service.RobinhoodRhDailySnapshotScheduler;
import com.svp.tracker.finance.service.YahooBatchQuoteService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AdminCronJobRunnerRegistry {

    private final Map<String, AdminCronJobRunnerDefinition> runners = new LinkedHashMap<>();

    public AdminCronJobRunnerRegistry(
            FinanceAlertEvaluationService financeAlertEvaluationService,
            YahooBatchQuoteService yahooBatchQuoteService,
            ObjectProvider<RobinhoodRhDailySnapshotScheduler> rhDailySnapshotScheduler,
            ObjectProvider<RobinhoodRhCryptoSnapshotScheduler> rhCryptoSnapshotScheduler,
            ObjectProvider<RobinhoodRhCryptoAutoTradeScheduler> rhCryptoAutoTradeScheduler,
            ObjectProvider<RobinhoodAgenticSyncScheduler> agenticSyncScheduler,
            RobinhoodAgenticAutoTradeScheduler autoTradeScheduler,
            StockTwitsIngestService stockTwitsIngestService,
            RedditIngestService redditIngestService,
            PredictsBaselineService predictsBaselineService,
            PredictsService predictsService,
            RobinhoodRhDailyTrackerProperties rhDailyTrackerProps,
            RobinhoodRhCryptoTrackerProperties rhCryptoTrackerProps,
            RobinhoodRhCryptoAutoTradeProperties rhCryptoAutoTradeProps,
            RobinhoodAgenticProperties agenticProps) {
        register(new AdminCronJobRunnerDefinition(
                "finance.alerts.evaluate",
                "Finance alert evaluation",
                "Evaluates enabled Finance stock alerts and sends notifications.",
                "Finance",
                financeAlertEvaluationService::scheduledEvaluate));
        register(new AdminCronJobRunnerDefinition(
                "finance.yahoo.quotes",
                "Yahoo / Alpha Vantage quote refresh",
                "Refreshes cached quotes for tracked finance symbols.",
                "Finance",
                yahooBatchQuoteService::refreshTrackedSymbolsHourly));
        register(new AdminCronJobRunnerDefinition(
                "finance.rh-daily-tracker.snapshot",
                "Robinhood Daily Tracker capture",
                "Hourly account snapshots for Daily Tracker (9 PM close row).",
                "Finance",
                () -> rhDailySnapshotScheduler.getObject().captureDailySnapshots(),
                () -> rhDailySnapshotScheduler.getIfAvailable() != null
                        && rhDailyTrackerProps.snapshotSchedulerActive()));
        register(new AdminCronJobRunnerDefinition(
                "finance.rh-crypto-tracker.snapshot",
                "Robinhood Crypto Tracker capture",
                "Periodic crypto portfolio snapshots via Crypto Trading API.",
                "Finance",
                () -> rhCryptoSnapshotScheduler.getObject().captureCryptoSnapshots(),
                () -> rhCryptoSnapshotScheduler.getIfAvailable() != null
                        && rhCryptoTrackerProps.snapshotSchedulerActive()));
        register(new AdminCronJobRunnerDefinition(
                "finance.rh-crypto-auto-trade.poll",
                "Robinhood Crypto auto-trade poll",
                "Evaluates Predicts-driven crypto auto-trade for connected users.",
                "Finance",
                () -> rhCryptoAutoTradeScheduler.getObject().pollAutoTrade(),
                () -> rhCryptoAutoTradeScheduler.getIfAvailable() != null
                        && rhCryptoAutoTradeProps.schedulerActive()));
        register(new AdminCronJobRunnerDefinition(
                "finance.robinhood-agentic.sync",
                "Robinhood Agentic sync",
                "Syncs Agentic trading connections on a cron schedule.",
                "Finance",
                () -> agenticSyncScheduler.getObject().scheduledSync(),
                () -> agenticSyncScheduler.getIfAvailable() != null
                        && agenticProps.syncCronEnabled()));
        register(new AdminCronJobRunnerDefinition(
                "finance.robinhood-agentic.auto-trade",
                "Robinhood Agentic auto-trade poll",
                "Evaluates auto-trade rules for connected Agentic users.",
                "Finance",
                autoTradeScheduler::pollAutoTrade));
        register(new AdminCronJobRunnerDefinition(
                "predicts.stocktwits.poll",
                "Predicts StockTwits poll",
                "Ingests StockTwits mentions for tracked tickers.",
                "Predicts",
                stockTwitsIngestService::pollCycle));
        register(new AdminCronJobRunnerDefinition(
                "predicts.reddit.poll",
                "Predicts Reddit poll",
                "Ingests Reddit posts for configured subreddits.",
                "Predicts",
                redditIngestService::pollCycle));
        register(new AdminCronJobRunnerDefinition(
                "predicts.baseline.nightly",
                "Predicts baseline + retention",
                "Recomputes baselines and purges old mentions.",
                "Predicts",
                predictsBaselineService::nightly));
        register(new AdminCronJobRunnerDefinition(
                "predicts.baseline.recompute",
                "Predicts baseline recompute only",
                "Recomputes baseline statistics without retention purge.",
                "Predicts",
                predictsBaselineService::recomputeBaselines));
        register(new AdminCronJobRunnerDefinition(
                "predicts.mentions.purge",
                "Predicts mention retention purge",
                "Deletes mentions older than the configured retention window.",
                "Predicts",
                predictsBaselineService::purgeOldMentions));
        register(new AdminCronJobRunnerDefinition(
                "predicts.auto-seed",
                "Predicts Robinhood auto-seed",
                "Auto-seeds Predicts tickers from Robinhood transaction symbols.",
                "Predicts",
                predictsService::autoSeedFromRobinhood));
    }

    private void register(AdminCronJobRunnerDefinition runner) {
        runners.put(runner.runnerKey(), runner);
    }

    public Collection<AdminCronJobRunnerDefinition> allRunners() {
        return List.copyOf(runners.values());
    }

    public Optional<AdminCronJobRunnerDefinition> find(String runnerKey) {
        return Optional.ofNullable(runners.get(runnerKey));
    }

    public void run(String runnerKey) {
        AdminCronJobRunnerDefinition runner =
                find(runnerKey).orElseThrow(() -> new IllegalArgumentException("Unknown runner: " + runnerKey));
        if (!runner.isAvailable()) {
            throw new IllegalStateException("Runner is not available in this environment: " + runnerKey);
        }
        runner.action().run();
    }
}
