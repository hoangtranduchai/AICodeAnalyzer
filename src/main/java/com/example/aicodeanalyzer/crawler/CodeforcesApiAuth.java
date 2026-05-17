package com.example.aicodeanalyzer.crawler;

import com.example.aicodeanalyzer.util.SecretUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

final class CodeforcesApiAuth {
    private static final String API_BASE = "https://codeforces.com/api/";

    private final String apiKey;
    private final String apiSecret;

    private CodeforcesApiAuth(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    static Optional<CodeforcesApiAuth> load() {
        String key = firstText(
                System.getProperty("codeforces.api-key"),
                SecretUtils.env("CODEFORCES_API_KEY"),
                localApplicationProperty("codeforces.api-key")
        );
        String secret = firstText(
                System.getProperty("codeforces.api-secret"),
                SecretUtils.env("CODEFORCES_API_SECRET"),
                localApplicationProperty("codeforces.api-secret")
        );
        if (!SecretUtils.hasText(key) || !SecretUtils.hasText(secret)) {
            return Optional.empty();
        }
        return Optional.of(new CodeforcesApiAuth(key, secret));
    }

    URI signedUri(String methodName, Map<String, String> methodParameters) {
        TreeMap<String, String> params = new TreeMap<>(Comparator.naturalOrder());
        if (methodParameters != null) {
            params.putAll(methodParameters);
        }
        params.put("apiKey", apiKey);
        params.put("time", String.valueOf(Instant.now().getEpochSecond()));

        String rand = "%06d".formatted(ThreadLocalRandom.current().nextInt(0, 1_000_000));
        String signatureBase = rand + "/" + methodName + "?" + canonicalQuery(params) + "#" + apiSecret;
        params.put("apiSig", rand + sha512Hex(signatureBase));
        return URI.create(API_BASE + methodName + "?" + encodedQuery(params));
    }

    private String canonicalQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey()
                        .thenComparing(Map.Entry.comparingByValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String encodedQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey()
                        .thenComparing(Map.Entry.comparingByValue()))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String sha512Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append("%02x".formatted(current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-512 is not available in this Java runtime.", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String localApplicationProperty(String key) {
        Path path = Path.of("application.properties");
        if (!Files.isRegularFile(path)) {
            path = Path.of("src", "main", "resources", "application.properties");
        }
        if (!Files.isRegularFile(path)) {
            return "";
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
            return properties.getProperty(key, "");
        } catch (IOException ex) {
            return "";
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (SecretUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
