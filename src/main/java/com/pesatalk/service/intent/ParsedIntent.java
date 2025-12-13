package com.pesatalk.service.intent;

import com.pesatalk.model.enums.Intent;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public record ParsedIntent(
    Intent intent,
    BigDecimal amount,
    String recipientIdentifier,
    String accountNumber,
    String billType,
    double confidence,
    Map<String, Object> metadata
) {
    public static Builder builder() {
        return new Builder();
    }

    public Optional<BigDecimal> getAmountOptional() {
        return Optional.ofNullable(amount);
    }

    public Optional<String> getRecipientOptional() {
        return Optional.ofNullable(recipientIdentifier).filter(s -> !s.isBlank());
    }

    public boolean hasHighConfidence() {
        return confidence >= 0.8;
    }

    public boolean hasMediumConfidence() {
        return confidence >= 0.5 && confidence < 0.8;
    }

    public static class Builder {
        private Intent intent = Intent.UNKNOWN;
        private BigDecimal amount;
        private String recipientIdentifier;
        private String accountNumber;
        private String billType;
        private double confidence = 0.0;
        private Map<String, Object> metadata = Map.of();

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

        public Builder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder billType(String billType) {
            this.billType = billType;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? metadata : Map.of();
            return this;
        }

        public ParsedIntent build() {
            return new ParsedIntent(
                intent,
                amount,
                recipientIdentifier,
                accountNumber,
                billType,
                confidence,
                metadata
            );
        }
    }

    public static ParsedIntent unknown() {
        return new ParsedIntent(Intent.UNKNOWN, null, null, null, null, 0.0, Map.of());
    }
}
