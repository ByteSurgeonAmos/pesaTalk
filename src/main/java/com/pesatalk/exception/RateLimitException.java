package com.pesatalk.exception;

public class RateLimitException extends PesaTalkException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super("RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
