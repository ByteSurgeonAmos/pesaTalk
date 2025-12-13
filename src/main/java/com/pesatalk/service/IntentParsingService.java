package com.pesatalk.service;

import com.pesatalk.model.enums.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IntentParsingService {

    private static final Logger log = LoggerFactory.getLogger(IntentParsingService.class);

    // Pattern for send money: "send 1500 to john" or "send KES 1,500 to 0712345678"
    private static final Pattern SEND_MONEY_PATTERN = Pattern.compile(
        "(?i)^(?:send|transfer|pay)\\s+" +
        "(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)\\s+" +
        "(?:to|for)\\s+" +
        "(.+?)\\s*$"
    );

    // Pattern for buy airtime: "buy airtime 100" or "airtime 100"
    private static final Pattern AIRTIME_PATTERN = Pattern.compile(
        "(?i)^(?:buy\\s+)?airtime\\s+(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)(?:\\s+(?:for\\s+)?(.+))?\\s*$"
    );

    // Amount extraction pattern for standalone amounts
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:kes\\s+)?([\\d,]+(?:\\.\\d{1,2})?)"
    );

    // Phone number pattern (Kenyan format)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^(?:\\+?254|0)?([17]\\d{8})$"
    );

    private static final List<String> BALANCE_KEYWORDS = List.of(
        "balance", "check balance", "my balance", "how much"
    );

    private static final List<String> HISTORY_KEYWORDS = List.of(
        "history", "transactions", "my transactions", "recent", "statement"
    );

    private static final List<String> HELP_KEYWORDS = List.of(
        "help", "menu", "options", "what can you do", "commands"
    );

    public IntentResult parseIntent(String text) {
        if (text == null || text.isBlank()) {
            return IntentResult.unknown();
        }

        String normalized = text.trim().toLowerCase();
        log.debug("Parsing intent from: {}", normalized);

        // Check for send money intent
        Matcher sendMoneyMatcher = SEND_MONEY_PATTERN.matcher(text.trim());
        if (sendMoneyMatcher.matches()) {
            BigDecimal amount = parseAmount(sendMoneyMatcher.group(1));
            String recipient = sendMoneyMatcher.group(2).trim();
            log.debug("Detected SEND_MONEY: amount={}, recipient={}", amount, "[REDACTED]");
            return new IntentResult(Intent.SEND_MONEY, amount, recipient);
        }

        // Check for airtime intent
        Matcher airtimeMatcher = AIRTIME_PATTERN.matcher(text.trim());
        if (airtimeMatcher.matches()) {
            BigDecimal amount = parseAmount(airtimeMatcher.group(1));
            String recipient = airtimeMatcher.group(2);
            return new IntentResult(Intent.BUY_AIRTIME, amount, recipient);
        }

        // Check for balance keywords
        if (matchesAny(normalized, BALANCE_KEYWORDS)) {
            return new IntentResult(Intent.CHECK_BALANCE, null, null);
        }

        // Check for history keywords
        if (matchesAny(normalized, HISTORY_KEYWORDS)) {
            return new IntentResult(Intent.TRANSACTION_HISTORY, null, null);
        }

        // Check for help keywords
        if (matchesAny(normalized, HELP_KEYWORDS)) {
            return new IntentResult(Intent.HELP, null, null);
        }

        return IntentResult.unknown();
    }

    private boolean matchesAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return null;
        }

        try {
            // Remove commas and whitespace
            String cleaned = amountStr.replaceAll("[,\\s]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", amountStr);
            return null;
        }
    }

    public boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.replaceAll("\\s", "")).matches();
    }

    public String normalizePhoneNumber(String phone) {
        if (phone == null) return null;

        String cleaned = phone.replaceAll("[\\s-]", "");
        Matcher matcher = PHONE_PATTERN.matcher(cleaned);

        if (matcher.matches()) {
            return "254" + matcher.group(1);
        }

        return null;
    }

    public record IntentResult(
        Intent intent,
        BigDecimal amount,
        String recipientIdentifier
    ) {
        public static IntentResult unknown() {
            return new IntentResult(Intent.UNKNOWN, null, null);
        }
    }
}
