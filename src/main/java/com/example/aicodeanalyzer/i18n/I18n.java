package com.example.aicodeanalyzer.i18n;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Small localization service for JavaFX screens.
 * Dịch vụ bản địa hóa nhỏ gọn cho các màn hình JavaFX.
 */
public final class I18n {
    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private final ObjectProperty<Language> language;
    private ResourceBundle bundle;

    private I18n(Language initialLanguage) {
        this.language = new SimpleObjectProperty<>(Objects.requireNonNull(initialLanguage, "initialLanguage"));
        this.bundle = loadBundle(initialLanguage);
        this.language.addListener((observable, oldValue, newValue) -> this.bundle = loadBundle(newValue));
    }

    public static I18n createDefault() {
        String configuredLanguage = System.getProperty("app.language");
        Language initialLanguage = configuredLanguage == null || configuredLanguage.isBlank()
                ? Language.fromCode(Locale.getDefault().getLanguage())
                : Language.fromCode(configuredLanguage);
        return new I18n(initialLanguage);
    }

    public Language language() {
        return language.get();
    }

    public ReadOnlyObjectProperty<Language> languageProperty() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language.set(Objects.requireNonNull(language, "language"));
    }

    public void toggle() {
        setLanguage(language().toggled());
    }

    public String text(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException ex) {
            pattern = "!" + key + "!";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, language().locale()).format(args);
    }

    public String switchTargetText() {
        return language().toggled().code().toUpperCase(Locale.ROOT);
    }

    private ResourceBundle loadBundle(Language language) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, language.locale());
    }
}
