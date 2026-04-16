package com.svp.tracker.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Pushes each formatted line into {@link LogLineBuffer}. */
public class MemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private PatternLayout layout;

    @Override
    public void start() {
        layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        layout.start();
        super.start();
    }

    @Override
    public void stop() {
        if (layout != null) {
            layout.stop();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted() || layout == null) {
            return;
        }
        String msg = event.getFormattedMessage();
        if (LogsApiPollNoise.matchesMessage(msg)) {
            return;
        }
        LogLineBuffer.addLine(layout.doLayout(event));
    }
}
