package com.svp.tracker.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emits one SLF4J line at each standard level (TRACE→ERROR) so the Logs tab can show how levels look
 * after {@code logging.level} filtering. Enable TRACE for this package to see all five.
 */
@Component
public class LogLevelSampleEmitter {

    private static final Logger log = LoggerFactory.getLogger(LogLevelSampleEmitter.class);

    public void emitSampleLines() {
        log.trace("[sample] TRACE — fine-grained diagnostic (enable TRACE for com.svp.tracker.diagnostics)");
        log.debug("[sample] DEBUG — developer detail");
        log.info("[sample] INFO — normal application event");
        log.warn("[sample] WARN — something unexpected");
        log.error("[sample] ERROR — failure or serious condition");
    }
}
