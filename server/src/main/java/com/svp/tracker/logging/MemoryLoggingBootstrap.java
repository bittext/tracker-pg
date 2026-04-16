package com.svp.tracker.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Attaches {@link MemoryLogAppender} to the Logback root logger on each application start (safe for
 * context refresh in the same JVM).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MemoryLoggingBootstrap implements ApplicationListener<ApplicationStartedEvent> {

    private static final String APPENDER_NAME = "IN_MEMORY";

    @Value("${tracker.logging.in-memory-max-lines:500}")
    private int maxLines;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        LogLineBuffer.setCapacity(maxLines);
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = lc.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(APPENDER_NAME) != null) {
            root.detachAppender(APPENDER_NAME);
        }
        MemoryLogAppender appender = new MemoryLogAppender();
        appender.setContext(lc);
        appender.setName(APPENDER_NAME);
        appender.start();
        root.addAppender(appender);
    }
}
