package com.pesatalk.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Component
public class ValidationUtil {

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s]+$");
    private static final Pattern SAFE_TEXT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s.,!?'-]+$");

    private static final BigDecimal MIN_TRANSACTION_AMOUNT = new BigDecimal("10");
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("70000");
    private static final BigDecimal MAX_DAILY_AMOUNT = new BigDecimal("150000");

    public boolean isValidAmount(BigDecimal amount) {
        if (amount == null) {
            return false;
        }

        return amount.compareTo(MIN_TRANSACTION_AMOUNT) >= 0
            && amount.compareTo(MAX_TRANSACTION_AMOUNT) <= 0;
    }

    public boolean isWithinDailyLimit(BigDecimal currentDailyTotal, BigDecimal newAmount) {
        if (currentDailyTotal == null || newAmount == null) {
            return false;
        }

        return currentDailyTotal.add(newAmount).compareTo(MAX_DAILY_AMOUNT) <= 0;
    }

    public boolean isValidAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }

        String trimmed = alias.trim();
        return trimmed.length() >= 2
            && trimmed.length() <= 50
            && ALPHANUMERIC_PATTERN.matcher(trimmed).matches();
    }

    public boolean isSafeText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return SAFE_TEXT_PATTERN.matcher(text).matches();
    }

    public String sanitizeText(String text) {
        if (text == null) {
            return null;
        }

        // Remove any potentially harmful characters
        return text.replaceAll("[^a-zA-Z0-9\\s.,!?'-]", "").trim();
    }

    public String sanitizeForLog(String sensitiveData) {
        if (sensitiveData == null) {
            return "[null]";
        }

        if (sensitiveData.length() <= 4) {
            return "****";
        }

        return sensitiveData.substring(0, 2) + "****" +
            sensitiveData.substring(sensitiveData.length() - 2);
    }

    public BigDecimal getMinTransactionAmount() {
        return MIN_TRANSACTION_AMOUNT;
    }

    public BigDecimal getMaxTransactionAmount() {
        return MAX_TRANSACTION_AMOUNT;
    }

    public BigDecimal getMaxDailyAmount() {
        return MAX_DAILY_AMOUNT;
    }
}
