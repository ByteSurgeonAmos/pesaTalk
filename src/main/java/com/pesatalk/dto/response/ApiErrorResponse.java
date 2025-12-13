package com.pesatalk.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldError> fieldErrors
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(
            Instant.now(),
            status,
            error,
            message,
            path,
            null
        );
    }

    public static ApiErrorResponse withFieldErrors(
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
    ) {
        return new ApiErrorResponse(
            Instant.now(),
            status,
            error,
            message,
            path,
            fieldErrors
        );
    }

    public record FieldError(
        String field,
        String message,
        Object rejectedValue
    ) {}
}
