package com.svp.tracker;

import com.svp.tracker.auth.config.AuthProperties;
import com.svp.tracker.auth.config.SmsProperties;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.config.WebProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    FinanceProperties.class,
    FinanceAlertProperties.class,
    AuthProperties.class,
    SmsProperties.class,
    WebProperties.class,
    JournalProperties.class
})
public class TrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackerApplication.class, args);
    }
}
