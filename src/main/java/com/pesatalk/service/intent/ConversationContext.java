package com.pesatalk.service.intent;

import com.pesatalk.model.enums.Intent;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ConversationContext implements Serializable {

    public enum PendingQuestion {
        NONE,
        AWAITING_AMOUNT,
        AWAITING_RECIPIENT,
        AWAITING_CONFIRMATION,
        AWAITING_PIN,
        AWAITING_CONTACT_NAME,
        AWAITING_CONTACT_PHONE
    }

    private UUID userId;
    private Intent currentIntent;
    private PendingQuestion pendingQuestion;
    private BigDecimal amount;
    private String recipient;
    private String recipientPhone;
    private UUID pendingTransactionId;
    private Instant lastInteractionAt;
    private int interactionCount;

    public ConversationContext() {
        this.pendingQuestion = PendingQuestion.NONE;
        this.lastInteractionAt = Instant.now();
        this.interactionCount = 0;
    }

    public ConversationContext(UUID userId) {
        this();
        this.userId = userId;
    }

    public boolean hasPendingQuestion() {
        return pendingQuestion != PendingQuestion.NONE;
    }

    public boolean isExpired() {
        // Context expires after 10 minutes of inactivity
        return lastInteractionAt.plusSeconds(600).isBefore(Instant.now());
    }

    public void reset() {
        this.currentIntent = null;
        this.pendingQuestion = PendingQuestion.NONE;
        this.amount = null;
        this.recipient = null;
        this.recipientPhone = null;
        this.pendingTransactionId = null;
    }

    public void touch() {
        this.lastInteractionAt = Instant.now();
        this.interactionCount++;
    }

    // Getters and setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Intent getCurrentIntent() {
        return currentIntent;
    }

    public void setCurrentIntent(Intent currentIntent) {
        this.currentIntent = currentIntent;
    }

    public PendingQuestion getPendingQuestion() {
        return pendingQuestion;
    }

    public void setPendingQuestion(PendingQuestion pendingQuestion) {
        this.pendingQuestion = pendingQuestion;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public UUID getPendingTransactionId() {
        return pendingTransactionId;
    }

    public void setPendingTransactionId(UUID pendingTransactionId) {
        this.pendingTransactionId = pendingTransactionId;
    }

    public Instant getLastInteractionAt() {
        return lastInteractionAt;
    }

    public int getInteractionCount() {
        return interactionCount;
    }
}
