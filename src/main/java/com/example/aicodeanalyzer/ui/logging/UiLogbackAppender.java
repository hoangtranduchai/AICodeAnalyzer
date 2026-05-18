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
        String message = eventObject.getFormattedMessage();
        if (message == null || message.isBlank()) {
            return;
        }

        ch.qos.logback.classic.Level level = eventObject.getLevel();
        if (ch.qos.logback.classic.Level.ERROR.equals(level)) {
            UiLogBus.publish("ERROR | " + shortLoggerName(eventObject.getLoggerName()) + " | " + message);
            return;
        }
        if (ch.qos.logback.classic.Level.WARN.equals(level)) {
            UiLogBus.publish("WARN | " + shortLoggerName(eventObject.getLoggerName()) + " | " + message);
            return;
        }
        UiLogBus.publish("System | " + message);
    }

    private String shortLoggerName(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return "Application";
        }
        int index = loggerName.lastIndexOf('.');
        return index >= 0 && index + 1 < loggerName.length()
                ? loggerName.substring(index + 1)
                : loggerName;
    }
}
