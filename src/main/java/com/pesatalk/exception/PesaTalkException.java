package com.pesatalk.exception;

public class PesaTalkException extends RuntimeException {

    private final String errorCode;

    public PesaTalkException(String message) {
        super(message);
        this.errorCode = "PESATALK_ERROR";
    }

    public PesaTalkException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PesaTalkException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
