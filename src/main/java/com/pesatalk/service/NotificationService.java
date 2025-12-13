package com.pesatalk.service;

import com.pesatalk.integration.whatsapp.WhatsAppClient;
import com.pesatalk.integration.whatsapp.dto.WhatsAppMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final WhatsAppClient whatsAppClient;

    public NotificationService(WhatsAppClient whatsAppClient) {
        this.whatsAppClient = whatsAppClient;
    }

    @Async
    public CompletableFuture<String> sendMessage(String recipientWhatsAppId, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var response = whatsAppClient.sendTextMessage(recipientWhatsAppId, message);
                String messageId = response.getFirstMessageId();
                log.debug("Sent message to {}, id={}", recipientWhatsAppId, messageId);
                return messageId;
            } catch (Exception e) {
                log.error("Failed to send message to {}: {}",
                    recipientWhatsAppId, e.getMessage(), e);
                return null;
            }
        });
    }

    @Async
    public CompletableFuture<String> sendInteractiveButtons(
        String recipientWhatsAppId,
        String bodyText,
        List<WhatsAppMessageRequest.Button> buttons
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var response = whatsAppClient.sendInteractiveButtons(
                    recipientWhatsAppId,
                    bodyText,
                    buttons
                );
                String messageId = response.getFirstMessageId();
                log.debug("Sent interactive message to {}, id={}", recipientWhatsAppId, messageId);
                return messageId;
            } catch (Exception e) {
                log.error("Failed to send interactive message to {}: {}",
                    recipientWhatsAppId, e.getMessage(), e);
                return null;
            }
        });
    }

    @Async
    public CompletableFuture<String> sendErrorMessage(String recipientWhatsAppId, String errorMessage) {
        return sendMessage(recipientWhatsAppId, errorMessage);
    }

    @Async
    public CompletableFuture<String> sendTransactionConfirmation(
        String recipientWhatsAppId,
        String amount,
        String recipientName,
        String receiptNumber
    ) {
        String message = String.format(
            "*Transaction Successful*\n\n" +
            "Amount: KES %s\n" +
            "To: %s\n" +
            "Receipt: %s\n\n" +
            "Thank you for using PesaTalk!",
            amount, recipientName, receiptNumber
        );
        return sendMessage(recipientWhatsAppId, message);
    }

    @Async
    public CompletableFuture<String> sendTransactionFailed(
        String recipientWhatsAppId,
        String reason
    ) {
        String message = String.format(
            "*Transaction Failed*\n\n" +
            "Reason: %s\n\n" +
            "Please try again or contact support if the issue persists.",
            reason
        );
        return sendMessage(recipientWhatsAppId, message);
    }
}
