package com.pesatalk.service.intent;

import com.pesatalk.model.enums.Intent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InteractiveResponseParser implements IntentParser {

    private static final Pattern CONFIRM_PATTERN = Pattern.compile("^confirm_(.+)$");
    private static final Pattern CANCEL_PATTERN = Pattern.compile("^cancel_(.+)$");
    private static final Pattern SELECT_CONTACT_PATTERN = Pattern.compile("^select_contact_(.+)$");
    private static final Pattern SELECT_AMOUNT_PATTERN = Pattern.compile("^select_amount_(.+)$");

    @Override
    public Intent getSupportedIntent() {
        return null; // Supports multiple intents
    }

    @Override
    public int getPriority() {
        return 200; // Highest priority - explicit button responses
    }

    @Override
    public Optional<ParsedIntent> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        // Check for confirm button
        Matcher confirmMatcher = CONFIRM_PATTERN.matcher(text);
        if (confirmMatcher.matches()) {
            String transactionId = confirmMatcher.group(1);
            return Optional.of(ParsedIntent.builder()
                .intent(Intent.CONFIRM_TRANSACTION)
                .confidence(1.0)
                .metadata(Map.of("transactionId", transactionId))
                .build());
        }

        // Check for cancel button
        Matcher cancelMatcher = CANCEL_PATTERN.matcher(text);
        if (cancelMatcher.matches()) {
            String transactionId = cancelMatcher.group(1);
            return Optional.of(ParsedIntent.builder()
                .intent(Intent.CANCEL_TRANSACTION)
                .confidence(1.0)
                .metadata(Map.of("transactionId", transactionId))
                .build());
        }

        // Check for contact selection
        Matcher contactMatcher = SELECT_CONTACT_PATTERN.matcher(text);
        if (contactMatcher.matches()) {
            String contactId = contactMatcher.group(1);
            return Optional.of(ParsedIntent.builder()
                .intent(Intent.SEND_MONEY)
                .recipientIdentifier(contactId)
                .confidence(1.0)
                .metadata(Map.of("contactId", contactId, "isSelection", true))
                .build());
        }

        // Check for amount selection
        Matcher amountMatcher = SELECT_AMOUNT_PATTERN.matcher(text);
        if (amountMatcher.matches()) {
            String amountStr = amountMatcher.group(1);
            try {
                java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);
                return Optional.of(ParsedIntent.builder()
                    .intent(Intent.BUY_AIRTIME)
                    .amount(amount)
                    .confidence(1.0)
                    .metadata(Map.of("isSelection", true))
                    .build());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    public Optional<ParsedIntent> parseButtonId(String buttonId) {
        return parse(buttonId);
    }

    public Optional<ParsedIntent> parseListItemId(String listItemId) {
        return parse(listItemId);
    }
}
