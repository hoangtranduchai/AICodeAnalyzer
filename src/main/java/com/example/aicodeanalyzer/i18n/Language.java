package com.example.aicodeanalyzer.i18n;

import java.util.Locale;

/**
 * Supported UI languages.
 * Ngôn ngữ giao diện được hỗ trợ.
 */
public enum Language {
    ENGLISH("en", Locale.ENGLISH, "English"),
    VIETNAMESE("vi", Locale.forLanguageTag("vi-VN"), "Tiếng Việt");

    private final String code;
    private final Locale locale;
    private final String displayName;

    Language(String code, Locale locale, String displayName) {
        this.code = code;
        this.locale = locale;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public Locale locale() {
        return locale;
    }

    public String displayName() {
        return displayName;
    }

    public Language toggled() {
        return this == ENGLISH ? VIETNAMESE : ENGLISH;
    }

    public static Language fromCode(String code) {
        if (code != null && code.toLowerCase(Locale.ROOT).startsWith("vi")) {
            return VIETNAMESE;
        }
        return ENGLISH;
    }
}
