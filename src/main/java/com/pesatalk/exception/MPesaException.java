package com.pesatalk.exception;

public class MPesaException extends PesaTalkException {

    public MPesaException(String message) {
        super("MPESA_ERROR", message);
    }

    public MPesaException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MPesaException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static MPesaException authenticationFailed(Throwable cause) {
        return new MPesaException("MPESA_AUTH_FAILED", "Failed to authenticate with MPesa API", cause);
    }

    public static MPesaException stkPushFailed(String resultDesc) {
        return new MPesaException("MPESA_STK_PUSH_FAILED", "STK Push failed: " + resultDesc);
    }

    public static MPesaException timeout() {
        return new MPesaException("MPESA_TIMEOUT", "MPesa API request timed out");
    }

    public static MPesaException serviceUnavailable() {
        return new MPesaException("MPESA_UNAVAILABLE", "MPesa service is currently unavailable");
    }
}
