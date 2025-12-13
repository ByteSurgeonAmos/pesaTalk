package com.pesatalk.service.intent;

import com.pesatalk.model.enums.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class IntentParsingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IntentParsingOrchestrator.class);

    private final List<IntentParser> parsers;
    private final InteractiveResponseParser interactiveParser;

    public IntentParsingOrchestrator(
        List<IntentParser> parsers,
        InteractiveResponseParser interactiveParser
    ) {
        // Sort parsers by priority (highest first)
        this.parsers = parsers.stream()
            .sorted(Comparator.comparingInt(IntentParser::getPriority).reversed())
            .toList();
        this.interactiveParser = interactiveParser;

        log.info("Initialized IntentParsingOrchestrator with {} parsers", parsers.size());
        parsers.forEach(p -> log.debug("Parser: {} (priority: {})",
            p.getClass().getSimpleName(), p.getPriority()));
    }

    public ParsedIntent parseText(String text) {
        if (text == null || text.isBlank()) {
            return ParsedIntent.unknown();
        }

        log.debug("Parsing text: {}", text.length() > 50 ? text.substring(0, 50) + "..." : text);

        ParsedIntent bestMatch = null;
        double bestConfidence = 0.0;

        // Try each parser in priority order
        for (IntentParser parser : parsers) {
            try {
                Optional<ParsedIntent> result = parser.parse(text);

                if (result.isPresent()) {
                    ParsedIntent parsed = result.get();

                    log.debug("Parser {} returned intent {} with confidence {}",
                        parser.getClass().getSimpleName(),
                        parsed.intent(),
                        parsed.confidence());

                    // If we get a high-confidence match, return immediately
                    if (parsed.confidence() >= 0.9) {
                        log.info("High confidence match: {} ({})",
                            parsed.intent(), parsed.confidence());
                        return parsed;
                    }

                    // Otherwise, track the best match
                    if (parsed.confidence() > bestConfidence) {
                        bestMatch = parsed;
                        bestConfidence = parsed.confidence();
                    }
                }
            } catch (Exception e) {
                log.error("Error in parser {}: {}",
                    parser.getClass().getSimpleName(), e.getMessage());
            }
        }

        if (bestMatch != null) {
            log.info("Best match: {} (confidence: {})", bestMatch.intent(), bestMatch.confidence());
            return bestMatch;
        }

        log.debug("No intent matched for input");
        return ParsedIntent.unknown();
    }

    public ParsedIntent parseButtonResponse(String buttonId) {
        return interactiveParser.parseButtonId(buttonId)
            .orElse(ParsedIntent.unknown());
    }

    public ParsedIntent parseListResponse(String listItemId) {
        return interactiveParser.parseListItemId(listItemId)
            .orElse(ParsedIntent.unknown());
    }

    public ParsedIntent parseWithContext(String text, ConversationContext context) {
        // First, try to parse as a direct response to a pending question
        if (context != null && context.hasPendingQuestion()) {
            Optional<ParsedIntent> contextualIntent = parseContextualResponse(text, context);
            if (contextualIntent.isPresent()) {
                return contextualIntent.get();
            }
        }

        // Otherwise, parse normally
        return parseText(text);
    }

    private Optional<ParsedIntent> parseContextualResponse(String text, ConversationContext context) {
        return switch (context.getPendingQuestion()) {
            case AWAITING_AMOUNT -> parseAmountResponse(text, context);
            case AWAITING_RECIPIENT -> parseRecipientResponse(text, context);
            case AWAITING_CONFIRMATION -> parseConfirmationResponse(text);
            default -> Optional.empty();
        };
    }

    private Optional<ParsedIntent> parseAmountResponse(String text, ConversationContext context) {
        String normalized = text.trim().replaceAll("[,\\s]", "");

        // Remove "kes" prefix if present
        normalized = normalized.replaceAll("(?i)^kes\\s*", "");

        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(normalized);
            return Optional.of(ParsedIntent.builder()
                .intent(context.getCurrentIntent())
                .amount(amount)
                .recipientIdentifier(context.getRecipient())
                .confidence(0.95)
                .build());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<ParsedIntent> parseRecipientResponse(String text, ConversationContext context) {
        String recipient = text.trim();
        if (recipient.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ParsedIntent.builder()
            .intent(context.getCurrentIntent())
            .amount(context.getAmount())
            .recipientIdentifier(recipient)
            .confidence(0.95)
            .build());
    }

    private Optional<ParsedIntent> parseConfirmationResponse(String text) {
        String normalized = text.trim().toLowerCase();

        if (normalized.matches("^(yes|confirm|ndio|sawa|ok|okay|proceed)$")) {
            return Optional.of(ParsedIntent.builder()
                .intent(Intent.CONFIRM_TRANSACTION)
                .confidence(0.95)
                .build());
        }

        if (normalized.matches("^(no|cancel|hapana|acha|stop)$")) {
            return Optional.of(ParsedIntent.builder()
                .intent(Intent.CANCEL_TRANSACTION)
                .confidence(0.95)
                .build());
        }

        return Optional.empty();
    }
}
