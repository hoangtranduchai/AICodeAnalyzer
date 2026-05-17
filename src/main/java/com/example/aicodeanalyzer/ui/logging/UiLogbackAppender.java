package com.example.aicodeanalyzer.ui.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback appender that mirrors application logs into the Workspace console.
 */
public class UiLogbackAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null) {
            return;
        }
        UiLogBus.publish("LOG " + eventObject.getLevel()
                + " " + eventObject.getLoggerName()
                + " - " + eventObject.getFormattedMessage());
    }
}
