package com.svp.tracker.admin.cron;

import com.svp.tracker.admin.cron.domain.AdminCronJob;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoTrackerProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminCronJobBuiltinCatalog {

    public List<AdminCronJob> builtInDefaults(
            FinanceAlertProperties alertProps,
            RobinhoodRhDailyTrackerProperties rhDailyTrackerProps,
            RobinhoodRhCryptoTrackerProperties rhCryptoTrackerProps,
            RobinhoodAgenticProperties agenticProps,
            RobinhoodAgenticAutoTradeProperties autoTradeProps,
            FinancePredictsProperties predictsProps) {
        List<AdminCronJob> jobs = new ArrayList<>();
        jobs.add(fixedDelay(
                "finance.alerts.evaluate",
                "Finance alert evaluation",
                "Evaluates enabled Finance stock alerts and sends notifications.",
                "Finance",
                "finance.alerts.evaluate",
                alertProps.pollFixedDelayMs(),
                0L));
        jobs.add(fixedDelay(
                "finance.yahoo.quotes",
                "Yahoo / Alpha Vantage quote refresh",
                "Refreshes cached quotes for tracked finance symbols.",
                "Finance",
                "finance.yahoo.quotes",
                3_600_000L,
                45_000L));
        if (rhDailyTrackerProps.snapshotSchedulerActive()) {
            jobs.add(cron(
                    "finance.rh-daily-tracker.snapshot",
                    "Robinhood Daily Tracker capture",
                    "Hourly account snapshots for Daily Tracker (9 PM close row).",
                    "Finance",
                    "finance.rh-daily-tracker.snapshot",
                    rhDailyTrackerProps.snapshotCron(),
                    rhDailyTrackerProps.snapshotZone()));
        }
        if (rhCryptoTrackerProps.snapshotSchedulerActive()) {
            jobs.add(cron(
                    "finance.rh-crypto-tracker.snapshot",
                    "Robinhood Crypto Tracker capture",
                    "Periodic crypto portfolio snapshots via Crypto Trading API.",
                    "Finance",
                    "finance.rh-crypto-tracker.snapshot",
                    rhCryptoTrackerProps.snapshotCron(),
                    "UTC"));
        }
        if (agenticProps.syncCronEnabled()) {
            jobs.add(cron(
                    "finance.robinhood-agentic.sync",
                    "Robinhood Agentic sync",
                    "Syncs Agentic trading connections on a cron schedule.",
                    "Finance",
                    "finance.robinhood-agentic.sync",
                    agenticProps.syncCron(),
                    "UTC"));
        }
        jobs.add(fixedDelay(
                "finance.robinhood-agentic.auto-trade",
                "Robinhood Agentic auto-trade poll",
                "Evaluates auto-trade rules for connected Agentic users.",
                "Finance",
                "finance.robinhood-agentic.auto-trade",
                autoTradeProps.pollFixedDelayMs(),
                autoTradeProps.pollInitialDelayMs()));
        jobs.add(fixedDelay(
                "predicts.stocktwits.poll",
                "Predicts StockTwits poll",
                "Ingests StockTwits mentions for tracked tickers.",
                "Predicts",
                "predicts.stocktwits.poll",
                predictsProps.stocktwits().pollIntervalSeconds() * 1000L,
                60_000L));
        jobs.add(fixedDelay(
                "predicts.reddit.poll",
                "Predicts Reddit poll",
                "Ingests Reddit posts for configured subreddits.",
                "Predicts",
                "predicts.reddit.poll",
                predictsProps.reddit().pollIntervalSeconds() * 1000L,
                120_000L));
        jobs.add(cron(
                "predicts.baseline.nightly",
                "Predicts baseline + retention",
                "Recomputes baselines and purges old mentions.",
                "Predicts",
                "predicts.baseline.nightly",
                "0 17 3 * * *",
                "UTC"));
        jobs.add(fixedDelay(
                "predicts.auto-seed",
                "Predicts Robinhood auto-seed",
                "Auto-seeds Predicts tickers from Robinhood transaction symbols.",
                "Predicts",
                "predicts.auto-seed",
                3_600_000L,
                90_000L));
        return jobs;
    }

    private static AdminCronJob cron(
            String jobKey,
            String displayName,
            String description,
            String category,
            String runnerKey,
            String cronExpression,
            String zoneId) {
        AdminCronJob job = base(jobKey, displayName, description, category, runnerKey);
        job.setScheduleType("CRON");
        job.setCronExpression(cronExpression.trim());
        job.setZoneId(zoneId);
        job.setInitialDelayMs(0L);
        return job;
    }

    private static AdminCronJob fixedDelay(
            String jobKey,
            String displayName,
            String description,
            String category,
            String runnerKey,
            long fixedDelayMs,
            long initialDelayMs) {
        AdminCronJob job = base(jobKey, displayName, description, category, runnerKey);
        job.setScheduleType("FIXED_DELAY");
        job.setFixedDelayMs(Math.max(1L, fixedDelayMs));
        job.setInitialDelayMs(Math.max(0L, initialDelayMs));
        job.setZoneId("UTC");
        return job;
    }

    private static AdminCronJob base(
            String jobKey, String displayName, String description, String category, String runnerKey) {
        AdminCronJob job = new AdminCronJob();
        job.setJobKey(jobKey);
        job.setDisplayName(displayName);
        job.setDescription(description);
        job.setCategory(category);
        job.setRunnerKey(runnerKey);
        job.setEnabled(true);
        job.setBuiltIn(true);
        return job;
    }
}
