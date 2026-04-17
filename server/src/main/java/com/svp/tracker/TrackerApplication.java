package com.svp.tracker;

import com.svp.tracker.auth.config.AuthProperties;
import com.svp.tracker.auth.config.SmsProperties;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.config.WebProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    FinanceProperties.class,
    AuthProperties.class,
    SmsProperties.class,
    WebProperties.class
})
public class TrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackerApplication.class, args);
    }
}
