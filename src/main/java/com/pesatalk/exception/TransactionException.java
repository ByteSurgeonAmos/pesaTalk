package com.pesatalk.exception;

public class TransactionException extends PesaTalkException {

    public TransactionException(String message) {
        super("TRANSACTION_ERROR", message);
    }

    public TransactionException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static TransactionException notFound(String identifier) {
        return new TransactionException("TRANSACTION_NOT_FOUND", "Transaction not found: " + identifier);
    }

    public static TransactionException invalidState(String currentState, String expectedState) {
        return new TransactionException(
            "INVALID_TRANSACTION_STATE",
            "Transaction is in state %s, expected %s".formatted(currentState, expectedState)
        );
    }

    public static TransactionException duplicate(String idempotencyKey) {
        return new TransactionException("DUPLICATE_TRANSACTION", "Duplicate transaction detected");
    }

    public static TransactionException limitExceeded(String limitType) {
        return new TransactionException("LIMIT_EXCEEDED", "Transaction limit exceeded: " + limitType);
    }
}
