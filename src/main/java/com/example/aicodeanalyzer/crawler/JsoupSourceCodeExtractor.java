package com.example.aicodeanalyzer.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.List;
import java.util.Optional;

final class JsoupSourceCodeExtractor {
    private static final int MIN_SOURCE_LENGTH = 12;
    private static final List<String> TRUSTED_SELECTORS = List.of(
        "pre#program-source-text",
        "pre.program-source",
        "pre#solutionCode",
        "pre#solution-code",
        "pre#source",
        "pre#submission-source",
        "pre.source-code",
        "textarea#program-source-text",
        "textarea#source",
        "code#program-source-text"
    );
    private static final List<String> SOURCE_SELECTORS = List.of(
        "pre.source",
        "pre.prettyprint",
        "pre",
        "textarea",
        "code"
    );

    Optional<String> extractTextSource(String html, URI baseUri) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Document document = Jsoup.parse(html, baseUri == null ? "" : baseUri.toString());
        for (String selector : TRUSTED_SELECTORS) {
            for (Element element : document.select(selector)) {
                String candidate = normalizeSource(element.wholeText());
                if (candidate.length() >= MIN_SOURCE_LENGTH) {
                    return Optional.of(candidate);
                }
            }
        }
        for (String selector : SOURCE_SELECTORS) {
            for (Element element : document.select(selector)) {
                String candidate = normalizeSource(element.wholeText());
                if (looksLikeSourceCode(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    Optional<URI> extractVJudgeSnapshotUri(String html, URI baseUri) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Document document = Jsoup.parse(html, baseUri == null ? "" : baseUri.toString());
        for (Element image : document.select("img[src*=solution/snapshot], img[src*=snapshot]")) {
            String absoluteUrl = image.absUrl("src");
            if (absoluteUrl != null && !absoluteUrl.isBlank()) {
                return Optional.of(URI.create(absoluteUrl));
            }
        }
        return Optional.empty();
    }

    private String normalizeSource(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    private boolean looksLikeSourceCode(String value) {
        if (value == null || value.length() < MIN_SOURCE_LENGTH) {
            return false;
        }
        String lower = value.toLowerCase();
        int signals = 0;
        if (lower.contains("#include") || lower.contains("using namespace")) {
            signals++;
        }
        if (lower.contains("int main") || lower.contains("public static void main") || lower.contains("def main")) {
            signals++;
        }
        if (value.contains("{") && value.contains("}")) {
            signals++;
        }
        if (value.contains(";")) {
            signals++;
        }
        if (lower.contains("import ") || lower.contains("from ")) {
            signals++;
        }
        return signals >= 1;
    }
}
