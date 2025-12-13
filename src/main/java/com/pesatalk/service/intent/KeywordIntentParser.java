package com.pesatalk.service.intent;

import com.pesatalk.model.enums.Intent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class KeywordIntentParser implements IntentParser {

    private record KeywordGroup(Intent intent, List<String> keywords, double confidence) {}

    private static final List<KeywordGroup> KEYWORD_GROUPS = List.of(
        new KeywordGroup(Intent.CHECK_BALANCE, List.of(
            "balance", "check balance", "my balance", "how much", "account balance",
            "salio", "pesa yangu"
        ), 0.9),

        new KeywordGroup(Intent.TRANSACTION_HISTORY, List.of(
            "history", "transactions", "my transactions", "recent", "statement",
            "what have i sent", "past transactions", "historia"
        ), 0.9),

        new KeywordGroup(Intent.HELP, List.of(
            "help", "menu", "options", "what can you do", "commands", "start",
            "how to", "how do i", "msaada", "?"
        ), 0.95),

        new KeywordGroup(Intent.ADD_CONTACT, List.of(
            "add contact", "save contact", "new contact", "save number",
            "remember", "add friend"
        ), 0.85),

        new KeywordGroup(Intent.LIST_CONTACTS, List.of(
            "my contacts", "list contacts", "show contacts", "contacts",
            "saved numbers", "friends"
        ), 0.85)
    );

    @Override
    public Intent getSupportedIntent() {
        return null; // Supports multiple intents
    }

    @Override
    public int getPriority() {
        return 50; // Lower priority than specific parsers
    }

    @Override
    public Optional<ParsedIntent> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String normalized = text.trim().toLowerCase();

        for (KeywordGroup group : KEYWORD_GROUPS) {
            for (String keyword : group.keywords()) {
                if (normalized.contains(keyword) || normalized.equals(keyword)) {
                    // Exact match gets full confidence, contains gets slightly less
                    double confidence = normalized.equals(keyword)
                        ? group.confidence()
                        : group.confidence() - 0.1;

                    return Optional.of(ParsedIntent.builder()
                        .intent(group.intent())
                        .confidence(confidence)
                        .metadata(Map.of("matchedKeyword", keyword))
                        .build());
                }
            }
        }

        return Optional.empty();
    }
}
