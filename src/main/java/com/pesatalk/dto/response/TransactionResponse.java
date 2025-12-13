package com.pesatalk.dto.response;

import com.pesatalk.model.Transaction;
import com.pesatalk.model.enums.TransactionStatus;
import com.pesatalk.model.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    TransactionType type,
    TransactionStatus status,
    BigDecimal amount,
    String currency,
    String recipientName,
    String mpesaReceiptNumber,
    Instant createdAt,
    Instant completedAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getTransactionType(),
            transaction.getStatus(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getRecipientName(),
            transaction.getMpesaReceiptNumber(),
            transaction.getCreatedAt(),
            transaction.getCompletedAt()
        );
    }
}
