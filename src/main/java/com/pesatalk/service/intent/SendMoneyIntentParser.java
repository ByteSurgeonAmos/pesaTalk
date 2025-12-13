package com.pesatalk.service.intent;

import com.pesatalk.model.enums.Intent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SendMoneyIntentParser implements IntentParser {

    private static final List<Pattern> SEND_PATTERNS = List.of(
        // "send 1500 to john" - most common
        Pattern.compile(
            "(?i)^(?:send|transfer|pay|give)\\s+" +
            "(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)\\s+" +
            "(?:to|for)\\s+" +
            "(.+?)\\s*$"
        ),
        // "send john 1500" - alternative order
        Pattern.compile(
            "(?i)^(?:send|transfer|pay|give)\\s+" +
            "(.+?)\\s+" +
            "(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)\\s*$"
        ),
        // "1500 to john" - without verb
        Pattern.compile(
            "(?i)^(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)\\s+" +
            "(?:to|for)\\s+" +
            "(.+?)\\s*$"
        ),
        // Swahili: "tuma 1500 kwa john"
        Pattern.compile(
            "(?i)^(?:tuma|peleka)\\s+" +
            "(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)\\s+" +
            "(?:kwa)\\s+" +
            "(.+?)\\s*$"
        )
    );

    // Pattern for phone numbers at the end
    private static final Pattern PHONE_AT_END = Pattern.compile(
        "(?i)^(?:send|transfer|pay|give|tuma)\\s+.+\\s+((?:\\+?254|0)?[17]\\d{8})\\s*$"
    );

    @Override
    public Intent getSupportedIntent() {
        return Intent.SEND_MONEY;
    }

    @Override
    public int getPriority() {
        return 100; // High priority
    }

    @Override
    public Optional<ParsedIntent> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String normalized = text.trim();

        // Try each pattern
        for (int i = 0; i < SEND_PATTERNS.size(); i++) {
            Pattern pattern = SEND_PATTERNS.get(i);
            Matcher matcher = pattern.matcher(normalized);

            if (matcher.matches()) {
                String amountStr;
                String recipient;

                // Pattern at index 1 has reversed order (recipient, amount)
                if (i == 1) {
                    recipient = matcher.group(1).trim();
                    amountStr = matcher.group(2);
                } else {
                    amountStr = matcher.group(1);
                    recipient = matcher.group(2).trim();
                }

                BigDecimal amount = parseAmount(amountStr);
                if (amount == null) {
                    continue;
                }

                // Calculate confidence based on pattern match quality
                double confidence = calculateConfidence(normalized, amount, recipient, i);

                return Optional.of(ParsedIntent.builder()
                    .intent(Intent.SEND_MONEY)
                    .amount(amount)
                    .recipientIdentifier(recipient)
                    .confidence(confidence)
                    .metadata(Map.of(
                        "patternIndex", i,
                        "originalText", normalized
                    ))
                    .build());
            }
        }

        return Optional.empty();
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return null;
        }

        try {
            String cleaned = amountStr.replaceAll("[,\\s]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double calculateConfidence(String text, BigDecimal amount, String recipient, int patternIndex) {
        double confidence = 0.7; // Base confidence for pattern match

        // Primary patterns (index 0) get higher confidence
        if (patternIndex == 0) {
            confidence += 0.15;
        }

        // Valid amount range increases confidence
        if (amount.compareTo(BigDecimal.TEN) >= 0 && amount.compareTo(new BigDecimal("70000")) <= 0) {
            confidence += 0.05;
        }

        // Phone number as recipient increases confidence
        if (recipient.matches("(?:\\+?254|0)?[17]\\d{8}")) {
            confidence += 0.1;
        }

        // Short recipient name (likely a saved contact) increases confidence
        if (recipient.length() <= 20 && !recipient.contains(" ")) {
            confidence += 0.05;
        }

        return Math.min(1.0, confidence);
    }
}
