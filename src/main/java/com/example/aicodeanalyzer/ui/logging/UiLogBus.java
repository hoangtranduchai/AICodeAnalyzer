package com.example.aicodeanalyzer.ui.logging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Small in-process bridge from backend loggers to the JavaFX workflow console.
 */
public final class UiLogBus {
    private static final int MAX_MESSAGE_LENGTH = 1_200;
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();

    private UiLogBus() {
    }

    public static void addListener(Consumer<String> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Consumer<String> listener) {
        LISTENERS.remove(listener);
    }

    public static void publish(String message) {
        if (message == null || message.isBlank() || LISTENERS.isEmpty()) {
            return;
        }
        String normalized = message.replaceAll("\\R+", " ").trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            normalized = normalized.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }
        for (Consumer<String> listener : LISTENERS) {
            listener.accept(normalized);
        }
    }
}
