package com.svp.tracker.admin.cron;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminCronJobBootstrap {

    private final AdminCronJobService cronJobService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Bootstrapping admin cron jobs");
        cronJobService.bootstrapAndStart();
    }
}
