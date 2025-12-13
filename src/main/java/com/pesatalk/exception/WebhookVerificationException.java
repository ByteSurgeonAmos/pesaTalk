package com.pesatalk.exception;

public class WebhookVerificationException extends PesaTalkException {

    public WebhookVerificationException(String message) {
        super("WEBHOOK_VERIFICATION_FAILED", message);
    }

    public static WebhookVerificationException invalidSignature() {
        return new WebhookVerificationException("Invalid webhook signature");
    }

    public static WebhookVerificationException invalidToken() {
        return new WebhookVerificationException("Invalid verify token");
    }

    public static WebhookVerificationException missingSignature() {
        return new WebhookVerificationException("Missing webhook signature header");
    }
}
