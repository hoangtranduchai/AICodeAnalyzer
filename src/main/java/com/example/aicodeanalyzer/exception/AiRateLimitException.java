package com.example.aicodeanalyzer.exception;

import java.time.Duration;

/**
 * EN: Signals an AI provider quota/rate-limit response that should pause the current analysis batch.
 * VI: Báo hiệu nhà cung cấp AI đã giới hạn quota/tần suất và lượt phân tích hiện tại nên tạm dừng.
 */
public class AiRateLimitException extends AnalyzerException {
    private final int statusCode;
    private final Duration retryAfter;

    public AiRateLimitException(String message, int statusCode, Duration retryAfter) {
        super(message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
    }

    public int statusCode() {
        return statusCode;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
