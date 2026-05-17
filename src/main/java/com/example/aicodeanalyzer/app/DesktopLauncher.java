package com.example.aicodeanalyzer.app;

/**
 * Plain main class used by executable jars. Keeping this class separate from
 * JavaFX Application avoids Java launcher edge cases with shaded artifacts.
 */
public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
