package com.svp.tracker;

import com.svp.tracker.auth.config.AuthProperties;
import com.svp.tracker.auth.config.SmsProperties;
import com.svp.tracker.config.BankingImportProperties;
import com.svp.tracker.config.BankingPlaidProperties;
import com.svp.tracker.config.FeedbackProperties;
import com.svp.tracker.config.GithubProperties;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.config.SecurityProperties;
import com.svp.tracker.config.WebProperties;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.management.config.ManagementAccountsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    FinanceProperties.class,
    BankingImportProperties.class,
    BankingPlaidProperties.class,
    FeedbackProperties.class,
    GithubProperties.class,
    FinanceAlertProperties.class,
    AuthProperties.class,
    SmsProperties.class,
    WebProperties.class,
    JournalProperties.class,
    SecurityProperties.class,
    ManagementAccountsProperties.class,
    FinancePredictsProperties.class,
    com.svp.tracker.config.RobinhoodAgenticProperties.class,
    com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties.class
})
public class TrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackerApplication.class, args);
    }
}
