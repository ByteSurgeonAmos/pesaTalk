package com.pesatalk.service;

import com.pesatalk.dto.ParsedMessage;
import com.pesatalk.integration.whatsapp.dto.WhatsAppWebhookPayload;
import com.pesatalk.model.User;
import com.pesatalk.model.enums.Intent;
import com.pesatalk.model.enums.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
public class MessageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessingService.class);
    private static final String PROCESSED_MESSAGES_KEY = "processed_messages:";
    private static final Duration MESSAGE_DEDUP_TTL = Duration.ofHours(24);

    private final UserService userService;
    private final IntentParsingService intentParsingService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;

    public MessageProcessingService(
        UserService userService,
        IntentParsingService intentParsingService,
        TransactionService transactionService,
        NotificationService notificationService,
        RedisTemplate<String, Object> redisTemplate
    ) {
        this.userService = userService;
        this.intentParsingService = intentParsingService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
    }

    public void processIncomingMessage(WhatsAppWebhookPayload.Message message, String senderName) {
        String messageId = message.id();

        // Idempotency check - skip already processed messages
        if (isMessageProcessed(messageId)) {
            log.debug("Skipping already processed message: {}", messageId);
            return;
        }

        try {
            // Parse the message
            ParsedMessage parsedMessage = parseMessage(message, senderName);
            log.info("Parsed message: intent={}, amount={}, recipient={}",
                parsedMessage.intent(),
                parsedMessage.amount(),
                parsedMessage.recipientIdentifier() != null ? "[REDACTED]" : null
            );

            // Get or create user
            User user = userService.getOrCreateUser(
                parsedMessage.senderWhatsAppId(),
                parsedMessage.senderPhoneNumber(),
                parsedMessage.senderName()
            );

            // Update last activity
            userService.recordActivity(user.getId());

            // Route to appropriate handler based on intent
            handleIntent(parsedMessage, user);

            // Mark message as processed
            markMessageProcessed(messageId);

        } catch (Exception e) {
            log.error("Error processing message {}: {}", messageId, e.getMessage(), e);
            notificationService.sendErrorMessage(
                message.from(),
                "Sorry, we encountered an error processing your request. Please try again."
            );
        }
    }

    private ParsedMessage parseMessage(WhatsAppWebhookPayload.Message message, String senderName) {
        MessageType messageType = determineMessageType(message.type());
        String rawContent = extractContent(message, messageType);

        ParsedMessage.Builder builder = ParsedMessage.builder()
            .messageId(message.id())
            .senderWhatsAppId(message.from())
            .senderPhoneNumber(message.from())
            .senderName(senderName)
            .messageType(messageType)
            .rawContent(rawContent);

        // Handle interactive responses
        if (messageType == MessageType.INTERACTIVE_BUTTON_REPLY && message.interactive() != null) {
            var buttonReply = message.interactive().buttonReply();
            if (buttonReply != null) {
                builder.buttonId(buttonReply.id());
                builder.intent(parseButtonIntent(buttonReply.id()));
            }
        } else if (messageType == MessageType.INTERACTIVE_LIST_REPLY && message.interactive() != null) {
            var listReply = message.interactive().listReply();
            if (listReply != null) {
                builder.listItemId(listReply.id());
            }
        } else if (messageType == MessageType.TEXT && rawContent != null) {
            // Parse natural language intent
            var intentResult = intentParsingService.parseIntent(rawContent);
            builder.intent(intentResult.intent())
                   .amount(intentResult.amount())
                   .recipientIdentifier(intentResult.recipientIdentifier());
        }

        return builder.build();
    }

    private MessageType determineMessageType(String type) {
        if (type == null) return MessageType.UNKNOWN;

        return switch (type.toLowerCase()) {
            case "text" -> MessageType.TEXT;
            case "interactive" -> MessageType.INTERACTIVE_BUTTON_REPLY;
            case "button" -> MessageType.INTERACTIVE_BUTTON_REPLY;
            case "image" -> MessageType.IMAGE;
            case "document" -> MessageType.DOCUMENT;
            default -> MessageType.UNKNOWN;
        };
    }

    private String extractContent(WhatsAppWebhookPayload.Message message, MessageType type) {
        return switch (type) {
            case TEXT -> message.text() != null ? message.text().body() : null;
            case INTERACTIVE_BUTTON_REPLY -> {
                if (message.interactive() != null && message.interactive().buttonReply() != null) {
                    yield message.interactive().buttonReply().title();
                }
                yield null;
            }
            case INTERACTIVE_LIST_REPLY -> {
                if (message.interactive() != null && message.interactive().listReply() != null) {
                    yield message.interactive().listReply().title();
                }
                yield null;
            }
            default -> null;
        };
    }

    private Intent parseButtonIntent(String buttonId) {
        if (buttonId == null) return Intent.UNKNOWN;

        return switch (buttonId) {
            case String s when s.startsWith("confirm_") -> Intent.CONFIRM_TRANSACTION;
            case String s when s.startsWith("cancel_") -> Intent.CANCEL_TRANSACTION;
            default -> Intent.UNKNOWN;
        };
    }

    private void handleIntent(ParsedMessage message, User user) {
        Intent intent = message.intent() != null ? message.intent() : Intent.UNKNOWN;

        switch (intent) {
            case SEND_MONEY -> handleSendMoney(message, user);
            case BUY_AIRTIME -> handleBuyAirtime(message, user);
            case CHECK_BALANCE -> handleCheckBalance(message, user);
            case TRANSACTION_HISTORY -> handleTransactionHistory(message, user);
            case CONFIRM_TRANSACTION -> handleConfirmTransaction(message, user);
            case CANCEL_TRANSACTION -> handleCancelTransaction(message, user);
            case HELP -> handleHelp(message, user);
            case UNKNOWN -> handleUnknown(message, user);
            default -> handleUnknown(message, user);
        }
    }

    private void handleSendMoney(ParsedMessage message, User user) {
        if (!message.hasAmount()) {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "Please specify the amount you want to send. For example: \"Send 1500 to John\""
            );
            return;
        }

        if (!message.hasRecipient()) {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "Please specify who you want to send money to. For example: \"Send 1500 to John\" or \"Send 1500 to 0712345678\""
            );
            return;
        }

        transactionService.initiateSendMoney(user, message);
    }

    private void handleBuyAirtime(ParsedMessage message, User user) {
        if (!message.hasAmount()) {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "Please specify the airtime amount. For example: \"Buy airtime 100\""
            );
            return;
        }

        // TODO: Implement airtime purchase
        notificationService.sendMessage(
            message.senderWhatsAppId(),
            "Airtime purchase is coming soon!"
        );
    }

    private void handleCheckBalance(ParsedMessage message, User user) {
        notificationService.sendMessage(
            message.senderWhatsAppId(),
            "To check your MPesa balance, dial *334# on your phone."
        );
    }

    private void handleTransactionHistory(ParsedMessage message, User user) {
        transactionService.sendTransactionHistory(user, message.senderWhatsAppId());
    }

    private void handleConfirmTransaction(ParsedMessage message, User user) {
        String transactionId = extractTransactionId(message.buttonId());
        if (transactionId != null) {
            transactionService.confirmTransaction(user, transactionId);
        }
    }

    private void handleCancelTransaction(ParsedMessage message, User user) {
        String transactionId = extractTransactionId(message.buttonId());
        if (transactionId != null) {
            transactionService.cancelTransaction(user, transactionId);
        }
    }

    private void handleHelp(ParsedMessage message, User user) {
        String helpText = """
            Welcome to PesaTalk! Here's what you can do:

            *Send Money*
            Just type: "Send 1500 to John" or "Send 500 to 0712345678"

            *Buy Airtime*
            Type: "Buy airtime 100"

            *Transaction History*
            Type: "My transactions" or "History"

            *Check Balance*
            Type: "Balance"

            Need help? Just type "Help" anytime!
            """;

        notificationService.sendMessage(message.senderWhatsAppId(), helpText);
    }

    private void handleUnknown(ParsedMessage message, User user) {
        Set<String> greetings = Set.of("hi", "hello", "hey", "hola", "jambo", "habari");
        String content = message.rawContent() != null ? message.rawContent().toLowerCase().trim() : "";

        if (greetings.contains(content)) {
            String greeting = user.getDisplayName() != null
                ? "Hello " + user.getDisplayName() + "!"
                : "Hello!";
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                greeting + " How can I help you today? Type \"Help\" to see what I can do."
            );
        } else {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "I didn't understand that. Type \"Help\" to see what I can do."
            );
        }
    }

    private String extractTransactionId(String buttonId) {
        if (buttonId == null) return null;

        if (buttonId.startsWith("confirm_")) {
            return buttonId.substring("confirm_".length());
        }
        if (buttonId.startsWith("cancel_")) {
            return buttonId.substring("cancel_".length());
        }
        return null;
    }

    private boolean isMessageProcessed(String messageId) {
        String key = PROCESSED_MESSAGES_KEY + messageId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markMessageProcessed(String messageId) {
        String key = PROCESSED_MESSAGES_KEY + messageId;
        redisTemplate.opsForValue().set(key, "1", MESSAGE_DEDUP_TTL);
    }
}
