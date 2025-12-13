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
public class AirtimeIntentParser implements IntentParser {

    private static final List<Pattern> AIRTIME_PATTERNS = List.of(
        // "buy airtime 100" or "buy airtime 100 for 0712345678"
        Pattern.compile(
            "(?i)^(?:buy\\s+)?(?:airtime|credit|bundles?)\\s+" +
            "(?:of\\s+)?(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)(?:\\s+(?:for|to)\\s+(.+))?\\s*$"
        ),
        // "100 airtime" or "100 airtime for mama"
        Pattern.compile(
            "(?i)^(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)\\s+" +
            "(?:airtime|credit)(?:\\s+(?:for|to)\\s+(.+))?\\s*$"
        ),
        // "top up 100" or "topup 100 for 0712345678"
        Pattern.compile(
            "(?i)^(?:top\\s*up|recharge)\\s+" +
            "(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)(?:\\s+(?:for|to)\\s+(.+))?\\s*$"
        ),
        // Swahili: "nunua airtime 100"
        Pattern.compile(
            "(?i)^(?:nunua)\\s+(?:airtime|credit)\\s+" +
            "(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)(?:\\s+(?:kwa)\\s+(.+))?\\s*$"
        )
    );

    // Valid airtime denominations
    private static final List<BigDecimal> COMMON_AMOUNTS = List.of(
        new BigDecimal("5"),
        new BigDecimal("10"),
        new BigDecimal("20"),
        new BigDecimal("50"),
        new BigDecimal("100"),
        new BigDecimal("200"),
        new BigDecimal("250"),
        new BigDecimal("500"),
        new BigDecimal("1000")
    );

    @Override
    public Intent getSupportedIntent() {
        return Intent.BUY_AIRTIME;
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public Optional<ParsedIntent> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String normalized = text.trim();

        for (int i = 0; i < AIRTIME_PATTERNS.size(); i++) {
            Pattern pattern = AIRTIME_PATTERNS.get(i);
            Matcher matcher = pattern.matcher(normalized);

            if (matcher.matches()) {
                String amountStr = matcher.group(1);
                String recipient = matcher.groupCount() >= 2 ? matcher.group(2) : null;

                BigDecimal amount = parseAmount(amountStr);
                if (amount == null) {
                    continue;
                }

                double confidence = calculateConfidence(amount, recipient, i);

                ParsedIntent.Builder builder = ParsedIntent.builder()
                    .intent(Intent.BUY_AIRTIME)
                    .amount(amount)
                    .confidence(confidence)
                    .metadata(Map.of("patternIndex", i));

                if (recipient != null && !recipient.isBlank()) {
                    builder.recipientIdentifier(recipient.trim());
                }

                return Optional.of(builder.build());
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

    private double calculateConfidence(BigDecimal amount, String recipient, int patternIndex) {
        double confidence = 0.75;

        // First pattern (most explicit) gets higher confidence
        if (patternIndex == 0) {
            confidence += 0.1;
        }

        // Common airtime amounts get higher confidence
        if (COMMON_AMOUNTS.stream().anyMatch(a -> a.compareTo(amount) == 0)) {
            confidence += 0.1;
        }

        // Valid amount range (5 - 10000)
        if (amount.compareTo(new BigDecimal("5")) >= 0 &&
            amount.compareTo(new BigDecimal("10000")) <= 0) {
            confidence += 0.05;
        }

        return Math.min(1.0, confidence);
    }
}
