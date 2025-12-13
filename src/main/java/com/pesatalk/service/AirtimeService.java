package com.pesatalk.service;

import com.pesatalk.exception.TransactionException;
import com.pesatalk.integration.mpesa.MPesaClient;
import com.pesatalk.integration.mpesa.dto.STKPushResponse;
import com.pesatalk.integration.whatsapp.dto.WhatsAppMessageRequest;
import com.pesatalk.model.Transaction;
import com.pesatalk.model.User;
import com.pesatalk.model.enums.TransactionStatus;
import com.pesatalk.model.enums.TransactionType;
import com.pesatalk.repository.TransactionRepository;
import com.pesatalk.service.intent.ConversationContext;
import com.pesatalk.service.intent.ParsedIntent;
import com.pesatalk.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AirtimeService {

    private static final Logger log = LoggerFactory.getLogger(AirtimeService.class);
    private static final int CONFIRMATION_TIMEOUT_MINUTES = 5;

    private static final List<BigDecimal> QUICK_AMOUNTS = List.of(
        new BigDecimal("20"),
        new BigDecimal("50"),
        new BigDecimal("100"),
        new BigDecimal("200"),
        new BigDecimal("500")
    );

    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final ConversationStateService conversationStateService;
    private final MPesaClient mpesaClient;
    private final PhoneNumberUtil phoneNumberUtil;

    @Value("${transaction.airtime.min-amount:5}")
    private BigDecimal minAmount;

    @Value("${transaction.airtime.max-amount:10000}")
    private BigDecimal maxAmount;

    public AirtimeService(
        TransactionRepository transactionRepository,
        NotificationService notificationService,
        ConversationStateService conversationStateService,
        MPesaClient mpesaClient,
        PhoneNumberUtil phoneNumberUtil
    ) {
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
        this.conversationStateService = conversationStateService;
        this.mpesaClient = mpesaClient;
        this.phoneNumberUtil = phoneNumberUtil;
    }

    public void handleAirtimeIntent(User user, ParsedIntent intent, String whatsAppId) {
        BigDecimal amount = intent.amount();
        String recipientIdentifier = intent.recipientIdentifier();

        // If no amount specified, show quick selection buttons
        if (amount == null) {
            sendAmountSelection(whatsAppId);
            return;
        }

        // Validate amount
        if (amount.compareTo(minAmount) < 0) {
            notificationService.sendMessage(whatsAppId,
                "Minimum airtime amount is KES " + minAmount + ". Please try again.");
            return;
        }

        if (amount.compareTo(maxAmount) > 0) {
            notificationService.sendMessage(whatsAppId,
                "Maximum airtime amount is KES " + maxAmount + ". Please try again.");
            return;
        }

        // Determine recipient (self or other)
        String recipientPhone = resolveRecipientPhone(user, recipientIdentifier, whatsAppId);

        // Create transaction and send confirmation
        initiateAirtimePurchase(user, amount, recipientPhone, whatsAppId);
    }

    private void sendAmountSelection(String whatsAppId) {
        List<WhatsAppMessageRequest.Button> buttons = List.of(
            WhatsAppMessageRequest.Button.of("select_amount_50", "KES 50"),
            WhatsAppMessageRequest.Button.of("select_amount_100", "KES 100"),
            WhatsAppMessageRequest.Button.of("select_amount_200", "KES 200")
        );

        notificationService.sendInteractiveButtons(
            whatsAppId,
            "How much airtime would you like to buy?\n\nSelect an amount below or type a custom amount (e.g., \"airtime 150\")",
            buttons
        );
    }

    private String resolveRecipientPhone(User user, String recipientIdentifier, String whatsAppId) {
        // If no recipient specified, buy for self
        if (recipientIdentifier == null || recipientIdentifier.isBlank()) {
            return whatsAppId;
        }

        // Try to normalize as phone number
        String normalized = phoneNumberUtil.normalizePhoneNumber(recipientIdentifier);
        if (normalized != null) {
            return normalized;
        }

        // Otherwise, it might be a contact name - for now, default to self
        // TODO: Lookup contact by name
        return whatsAppId;
    }

    @Transactional
    public void initiateAirtimePurchase(User user, BigDecimal amount, String recipientPhone, String whatsAppId) {
        // Generate idempotency key
        String idempotencyKey = generateIdempotencyKey(user.getId(), recipientPhone, amount);

        // Check for duplicate
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            notificationService.sendMessage(whatsAppId,
                "You already have a similar pending transaction. Please confirm or cancel it first.");
            return;
        }

        boolean isSelf = recipientPhone.equals(whatsAppId);
        String recipientDisplay = isSelf ? "yourself" : phoneNumberUtil.maskPhoneNumber(recipientPhone);

        // Create transaction
        Transaction transaction = Transaction.builder()
            .idempotencyKey(idempotencyKey)
            .sender(user)
            .transactionType(TransactionType.BUY_AIRTIME)
            .status(TransactionStatus.PENDING_CONFIRMATION)
            .amount(amount)
            .currency("KES")
            .recipientPhoneHash(phoneNumberUtil.hashPhoneNumber(recipientPhone))
            .recipientPhoneEncrypted(phoneNumberUtil.encryptPhoneNumber(recipientPhone))
            .recipientName(isSelf ? "Self" : recipientDisplay)
            .accountReference("Airtime")
            .description("Buy airtime via PesaTalk")
            .confirmationExpiresAt(Instant.now().plus(CONFIRMATION_TIMEOUT_MINUTES, ChronoUnit.MINUTES))
            .build();

        Transaction saved = transactionRepository.save(transaction);
        log.info("Created airtime transaction: id={}", saved.getId());

        // Send confirmation request
        sendConfirmationRequest(whatsAppId, saved, recipientDisplay, amount);
    }

    private void sendConfirmationRequest(String whatsAppId, Transaction transaction, String recipient, BigDecimal amount) {
        String message = String.format(
            "Please confirm your airtime purchase:\n\n" +
            "*Amount:* KES %,.2f\n" +
            "*For:* %s\n\n" +
            "This request expires in %d minutes.",
            amount, recipient, CONFIRMATION_TIMEOUT_MINUTES
        );

        List<WhatsAppMessageRequest.Button> buttons = List.of(
            WhatsAppMessageRequest.Button.of("confirm_" + transaction.getId(), "Confirm"),
            WhatsAppMessageRequest.Button.of("cancel_" + transaction.getId(), "Cancel")
        );

        notificationService.sendInteractiveButtons(whatsAppId, message, buttons);
    }

    @Transactional
    public void processConfirmedAirtime(Transaction transaction) {
        String recipientPhone = phoneNumberUtil.decryptPhoneNumber(
            transaction.getRecipientPhoneEncrypted()
        );

        try {
            transaction.transitionTo(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            // Initiate STK Push for airtime
            STKPushResponse response = mpesaClient.initiateSTKPush(
                recipientPhone,
                transaction.getAmount(),
                "Airtime",
                "PesaTalk Airtime"
            );

            if (response.isSuccessful()) {
                transaction.setMerchantRequestId(response.merchantRequestID());
                transaction.setCheckoutRequestId(response.checkoutRequestID());
                transaction.transitionTo(TransactionStatus.STK_PUSHED);
                transactionRepository.save(transaction);

                notificationService.sendMessage(
                    transaction.getSender().getWhatsAppId(),
                    "Please enter your MPesa PIN on your phone to complete the airtime purchase."
                );
            } else {
                transaction.transitionTo(TransactionStatus.FAILED);
                transaction.setFailureReason(response.getErrorDetails());
                transactionRepository.save(transaction);

                notificationService.sendMessage(
                    transaction.getSender().getWhatsAppId(),
                    "Airtime purchase failed: " + response.getErrorDetails()
                );
            }
        } catch (Exception e) {
            log.error("Error processing airtime transaction {}: {}", transaction.getId(), e.getMessage(), e);
            transaction.transitionTo(TransactionStatus.FAILED);
            transaction.setFailureReason("Processing error: " + e.getMessage());
            transactionRepository.save(transaction);

            notificationService.sendMessage(
                transaction.getSender().getWhatsAppId(),
                "An error occurred while processing your airtime purchase. Please try again."
            );
        }
    }

    private String generateIdempotencyKey(UUID userId, String recipientPhone, BigDecimal amount) {
        String data = userId + ":AIRTIME:" + recipientPhone + ":" + amount + ":" +
            Instant.now().truncatedTo(ChronoUnit.MINUTES);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
