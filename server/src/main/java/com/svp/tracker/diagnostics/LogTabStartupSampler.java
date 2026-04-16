package com.svp.tracker.diagnostics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/** Optionally emits multi-level sample lines once the app is ready (after the in-memory appender exists). */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogTabStartupSampler implements ApplicationListener<ApplicationReadyEvent> {

    private final LogLevelSampleEmitter sampleEmitter;

    @Value("${tracker.logging.emit-level-samples-on-startup:true}")
    private boolean emitOnStartup;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!emitOnStartup) {
            return;
        }
        log.info("Emitting SLF4J level samples for Logs tab (configure logging.level to control visibility)");
        sampleEmitter.emitSampleLines();
    }
}
