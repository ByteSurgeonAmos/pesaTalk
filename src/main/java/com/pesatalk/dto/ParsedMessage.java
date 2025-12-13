package com.pesatalk.dto;

import com.pesatalk.model.enums.Intent;
import com.pesatalk.model.enums.MessageType;

import java.math.BigDecimal;
import java.util.Optional;

public record ParsedMessage(
    String messageId,
    String senderWhatsAppId,
    String senderPhoneNumber,
    String senderName,
    MessageType messageType,
    String rawContent,
    Intent intent,
    BigDecimal amount,
    String recipientIdentifier,
    String buttonId,
    String listItemId
) {
    public static Builder builder() {
        return new Builder();
    }

    public boolean hasAmount() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasRecipient() {
        return recipientIdentifier != null && !recipientIdentifier.isBlank();
    }

    public Optional<BigDecimal> getAmountOptional() {
        return Optional.ofNullable(amount);
    }

    public Optional<String> getRecipientOptional() {
        return Optional.ofNullable(recipientIdentifier).filter(s -> !s.isBlank());
    }

    public static class Builder {
        private String messageId;
        private String senderWhatsAppId;
        private String senderPhoneNumber;
        private String senderName;
        private MessageType messageType;
        private String rawContent;
        private Intent intent;
        private BigDecimal amount;
        private String recipientIdentifier;
        private String buttonId;
        private String listItemId;

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder senderWhatsAppId(String senderWhatsAppId) {
            this.senderWhatsAppId = senderWhatsAppId;
            return this;
        }

        public Builder senderPhoneNumber(String senderPhoneNumber) {
            this.senderPhoneNumber = senderPhoneNumber;
            return this;
        }

        public Builder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public Builder messageType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder rawContent(String rawContent) {
            this.rawContent = rawContent;
            return this;
        }

        public Builder intent(Intent intent) {
            this.intent = intent;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder recipientIdentifier(String recipientIdentifier) {
            this.recipientIdentifier = recipientIdentifier;
            return this;
        }

        public Builder buttonId(String buttonId) {
            this.buttonId = buttonId;
            return this;
        }

        public Builder listItemId(String listItemId) {
            this.listItemId = listItemId;
            return this;
        }

        public ParsedMessage build() {
            return new ParsedMessage(
                messageId,
                senderWhatsAppId,
                senderPhoneNumber,
                senderName,
                messageType,
                rawContent,
                intent,
                amount,
                recipientIdentifier,
                buttonId,
                listItemId
            );
        }
    }
}
