package com.pesatalk.model;

import com.pesatalk.model.enums.TransactionStatus;
import com.pesatalk.model.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_sender_id", columnList = "sender_id"),
    @Index(name = "idx_transactions_status", columnList = "status"),
    @Index(name = "idx_transactions_mpesa_ref", columnList = "mpesa_receipt_number"),
    @Index(name = "idx_transactions_checkout_id", columnList = "checkout_request_id"),
    @Index(name = "idx_transactions_idempotency", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_transactions_created_at", columnList = "created_at")
})
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.INITIATED;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "recipient_phone_hash", nullable = false, length = 64)
    private String recipientPhoneHash;

    @Column(name = "recipient_phone_encrypted")
    private String recipientPhoneEncrypted;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "account_reference", length = 50)
    private String accountReference;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "merchant_request_id", length = 50)
    private String merchantRequestId;

    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestId;

    @Column(name = "mpesa_receipt_number", length = 30)
    private String mpesaReceiptNumber;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_description", length = 500)
    private String resultDescription;

    @Column(name = "confirmation_expires_at")
    private Instant confirmationExpiresAt;

    @Column(name = "stk_pushed_at")
    private Instant stkPushedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "whatsapp_message_id", length = 100)
    private String whatsappMessageId;

    public boolean canTransitionTo(TransactionStatus newStatus) {
        return switch (this.status) {
            case INITIATED -> newStatus == TransactionStatus.PENDING_CONFIRMATION
                           || newStatus == TransactionStatus.CANCELLED;
            case PENDING_CONFIRMATION -> newStatus == TransactionStatus.CONFIRMED
                                      || newStatus == TransactionStatus.CANCELLED
                                      || newStatus == TransactionStatus.EXPIRED;
            case CONFIRMED -> newStatus == TransactionStatus.PROCESSING
                           || newStatus == TransactionStatus.CANCELLED;
            case PROCESSING -> newStatus == TransactionStatus.STK_PUSHED
                            || newStatus == TransactionStatus.FAILED;
            case STK_PUSHED -> newStatus == TransactionStatus.COMPLETED
                            || newStatus == TransactionStatus.FAILED;
            case COMPLETED -> newStatus == TransactionStatus.REFUNDED;
            case FAILED, CANCELLED, EXPIRED, REFUNDED -> false;
        };
    }

    public void transitionTo(TransactionStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition from %s to %s".formatted(this.status, newStatus)
            );
        }
        this.status = newStatus;

        switch (newStatus) {
            case STK_PUSHED -> this.stkPushedAt = Instant.now();
            case COMPLETED -> this.completedAt = Instant.now();
            case FAILED, CANCELLED, EXPIRED -> this.failedAt = Instant.now();
            default -> {}
        }
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public boolean isTerminal() {
        return this.status == TransactionStatus.COMPLETED
            || this.status == TransactionStatus.FAILED
            || this.status == TransactionStatus.CANCELLED
            || this.status == TransactionStatus.EXPIRED
            || this.status == TransactionStatus.REFUNDED;
    }
}
