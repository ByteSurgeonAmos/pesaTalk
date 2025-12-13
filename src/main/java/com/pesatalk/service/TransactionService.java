package com.pesatalk.service;

import com.pesatalk.dto.ParsedMessage;
import com.pesatalk.exception.TransactionException;
import com.pesatalk.integration.mpesa.MPesaClient;
import com.pesatalk.integration.mpesa.dto.STKCallbackPayload;
import com.pesatalk.integration.mpesa.dto.STKPushResponse;
import com.pesatalk.integration.whatsapp.dto.WhatsAppMessageRequest;
import com.pesatalk.model.Contact;
import com.pesatalk.model.Transaction;
import com.pesatalk.model.User;
import com.pesatalk.model.enums.TransactionStatus;
import com.pesatalk.model.enums.TransactionType;
import com.pesatalk.repository.TransactionRepository;
import com.pesatalk.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final int CONFIRMATION_TIMEOUT_MINUTES = 5;

    private final TransactionRepository transactionRepository;
    private final ContactService contactService;
    private final NotificationService notificationService;
    private final MPesaClient mpesaClient;
    private final PhoneNumberUtil phoneNumberUtil;
    private final IntentParsingService intentParsingService;

    @Value("${transaction.daily-limit:150000}")
    private BigDecimal dailyLimit;

    @Value("${transaction.min-amount:10}")
    private BigDecimal minAmount;

    @Value("${transaction.max-amount:70000}")
    private BigDecimal maxAmount;

    public TransactionService(
        TransactionRepository transactionRepository,
        ContactService contactService,
        NotificationService notificationService,
        MPesaClient mpesaClient,
        PhoneNumberUtil phoneNumberUtil,
        IntentParsingService intentParsingService
    ) {
        this.transactionRepository = transactionRepository;
        this.contactService = contactService;
        this.notificationService = notificationService;
        this.mpesaClient = mpesaClient;
        this.phoneNumberUtil = phoneNumberUtil;
        this.intentParsingService = intentParsingService;
    }

    @Transactional
    public void initiateSendMoney(User user, ParsedMessage message) {
        BigDecimal amount = message.amount();
        String recipientIdentifier = message.recipientIdentifier();

        // Validate amount
        if (amount.compareTo(minAmount) < 0) {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "Minimum amount is KES " + minAmount + ". Please try again."
            );
            return;
        }

        if (amount.compareTo(maxAmount) > 0) {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "Maximum amount per transaction is KES " + maxAmount + ". Please try again."
            );
            return;
        }

        // Resolve recipient phone number
        String recipientPhone = resolveRecipientPhone(user, recipientIdentifier);
        if (recipientPhone == null) {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "I couldn't find the recipient. Please use a phone number (0712345678) or save a contact first."
            );
            return;
        }

        String recipientName = resolveRecipientName(user, recipientIdentifier, recipientPhone);

        // Generate idempotency key
        String idempotencyKey = generateIdempotencyKey(user.getId(), recipientPhone, amount);

        // Check for duplicate
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            notificationService.sendMessage(
                message.senderWhatsAppId(),
                "You already have a similar pending transaction. Please confirm or cancel it first."
            );
            return;
        }

        // Create transaction
        Transaction transaction = Transaction.builder()
            .idempotencyKey(idempotencyKey)
            .sender(user)
            .transactionType(TransactionType.SEND_MONEY)
            .status(TransactionStatus.PENDING_CONFIRMATION)
            .amount(amount)
            .currency("KES")
            .recipientPhoneHash(phoneNumberUtil.hashPhoneNumber(recipientPhone))
            .recipientPhoneEncrypted(phoneNumberUtil.encryptPhoneNumber(recipientPhone))
            .recipientName(recipientName)
            .accountReference("PesaTalk")
            .description("Send money via PesaTalk")
            .confirmationExpiresAt(Instant.now().plus(CONFIRMATION_TIMEOUT_MINUTES, ChronoUnit.MINUTES))
            .whatsappMessageId(message.messageId())
            .build();

        Transaction saved = transactionRepository.save(transaction);
        log.info("Created transaction: id={}, status={}", saved.getId(), saved.getStatus());

        // Send confirmation request
        sendConfirmationRequest(message.senderWhatsAppId(), saved, recipientName, amount);
    }

    private void sendConfirmationRequest(
        String whatsAppId,
        Transaction transaction,
        String recipientName,
        BigDecimal amount
    ) {
        String confirmMessage = String.format(
            "Please confirm:\n\n" +
            "*Send KES %,.2f to %s*\n\n" +
            "This request expires in %d minutes.",
            amount, recipientName, CONFIRMATION_TIMEOUT_MINUTES
        );

        List<WhatsAppMessageRequest.Button> buttons = List.of(
            WhatsAppMessageRequest.Button.of("confirm_" + transaction.getId(), "Confirm"),
            WhatsAppMessageRequest.Button.of("cancel_" + transaction.getId(), "Cancel")
        );

        notificationService.sendInteractiveButtons(whatsAppId, confirmMessage, buttons);
    }

    @Transactional
    public void confirmTransaction(User user, String transactionIdStr) {
        UUID transactionId;
        try {
            transactionId = UUID.fromString(transactionIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transaction ID format: {}", transactionIdStr);
            return;
        }

        Transaction transaction = transactionRepository.findByIdWithLock(transactionId)
            .orElseThrow(() -> TransactionException.notFound(transactionIdStr));

        // Verify ownership
        if (!transaction.getSender().getId().equals(user.getId())) {
            log.warn("User {} tried to confirm transaction {} owned by {}",
                user.getId(), transactionId, transaction.getSender().getId());
            return;
        }

        // Verify state
        if (transaction.getStatus() != TransactionStatus.PENDING_CONFIRMATION) {
            notificationService.sendMessage(
                user.getWhatsAppId(),
                "This transaction is no longer pending confirmation."
            );
            return;
        }

        // Check expiration
        if (Instant.now().isAfter(transaction.getConfirmationExpiresAt())) {
            transaction.transitionTo(TransactionStatus.EXPIRED);
            transactionRepository.save(transaction);
            notificationService.sendMessage(
                user.getWhatsAppId(),
                "This transaction has expired. Please start a new transaction."
            );
            return;
        }

        // Transition to CONFIRMED
        transaction.transitionTo(TransactionStatus.CONFIRMED);
        transactionRepository.save(transaction);

        // Process the transaction
        processTransaction(transaction);
    }

    @Transactional
    public void cancelTransaction(User user, String transactionIdStr) {
        UUID transactionId;
        try {
            transactionId = UUID.fromString(transactionIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Transaction transaction = transactionRepository.findByIdWithLock(transactionId)
            .orElseThrow(() -> TransactionException.notFound(transactionIdStr));

        if (!transaction.getSender().getId().equals(user.getId())) {
            return;
        }

        if (transaction.canTransitionTo(TransactionStatus.CANCELLED)) {
            transaction.transitionTo(TransactionStatus.CANCELLED);
            transaction.setFailureReason("Cancelled by user");
            transactionRepository.save(transaction);

            notificationService.sendMessage(
                user.getWhatsAppId(),
                "Transaction cancelled."
            );
        }
    }

    private void processTransaction(Transaction transaction) {
        String recipientPhone = phoneNumberUtil.decryptPhoneNumber(
            transaction.getRecipientPhoneEncrypted()
        );

        try {
            transaction.transitionTo(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            // Initiate STK Push
            STKPushResponse response = mpesaClient.initiateSTKPush(
                recipientPhone,
                transaction.getAmount(),
                transaction.getAccountReference(),
                transaction.getDescription()
            );

            if (response.isSuccessful()) {
                transaction.setMerchantRequestId(response.merchantRequestID());
                transaction.setCheckoutRequestId(response.checkoutRequestID());
                transaction.transitionTo(TransactionStatus.STK_PUSHED);
                transactionRepository.save(transaction);

                notificationService.sendMessage(
                    transaction.getSender().getWhatsAppId(),
                    "Please enter your MPesa PIN on your phone to complete the transaction."
                );
            } else {
                transaction.transitionTo(TransactionStatus.FAILED);
                transaction.setFailureReason(response.getErrorDetails());
                transactionRepository.save(transaction);

                notificationService.sendMessage(
                    transaction.getSender().getWhatsAppId(),
                    "Transaction failed: " + response.getErrorDetails()
                );
            }
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", transaction.getId(), e.getMessage(), e);
            transaction.transitionTo(TransactionStatus.FAILED);
            transaction.setFailureReason("Processing error: " + e.getMessage());
            transactionRepository.save(transaction);

            notificationService.sendMessage(
                transaction.getSender().getWhatsAppId(),
                "An error occurred while processing your transaction. Please try again."
            );
        }
    }

    @Transactional
    public void processSTKCallback(STKCallbackPayload callback) {
        String checkoutRequestId = callback.getCheckoutRequestId();
        if (checkoutRequestId == null) {
            log.warn("Received callback without checkout request ID");
            return;
        }

        Optional<Transaction> transactionOpt = transactionRepository
            .findByCheckoutRequestIdWithLock(checkoutRequestId);

        if (transactionOpt.isEmpty()) {
            log.warn("No transaction found for checkout request: {}", checkoutRequestId);
            return;
        }

        Transaction transaction = transactionOpt.get();

        if (transaction.isTerminal()) {
            log.info("Transaction {} already in terminal state: {}",
                transaction.getId(), transaction.getStatus());
            return;
        }

        if (callback.isSuccessful()) {
            transaction.transitionTo(TransactionStatus.COMPLETED);
            transaction.setMpesaReceiptNumber(callback.getMpesaReceiptNumber());
            transaction.setResultCode(callback.getResultCode());
            transaction.setResultDescription(callback.getResultDescription());
            transactionRepository.save(transaction);

            notificationService.sendMessage(
                transaction.getSender().getWhatsAppId(),
                String.format(
                    "Transaction successful!\n\n" +
                    "Amount: KES %,.2f\n" +
                    "To: %s\n" +
                    "Receipt: %s\n\n" +
                    "Thank you for using PesaTalk!",
                    transaction.getAmount(),
                    transaction.getRecipientName(),
                    callback.getMpesaReceiptNumber()
                )
            );

            // Update contact transaction count
            contactService.incrementContactTransactionCount(
                transaction.getSender().getId(),
                transaction.getRecipientPhoneHash()
            );
        } else {
            transaction.transitionTo(TransactionStatus.FAILED);
            transaction.setResultCode(callback.getResultCode());
            transaction.setResultDescription(callback.getResultDescription());
            transaction.setFailureReason(callback.getResultDescription());
            transactionRepository.save(transaction);

            notificationService.sendMessage(
                transaction.getSender().getWhatsAppId(),
                "Transaction failed: " + callback.getResultDescription()
            );
        }
    }

    public void sendTransactionHistory(User user, String whatsAppId) {
        var transactions = transactionRepository
            .findBySenderIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 5));

        if (transactions.isEmpty()) {
            notificationService.sendMessage(whatsAppId, "You have no recent transactions.");
            return;
        }

        StringBuilder sb = new StringBuilder("*Recent Transactions*\n\n");
        for (Transaction tx : transactions) {
            sb.append(String.format(
                "%s | KES %,.2f to %s | %s\n",
                tx.getCreatedAt().toString().substring(0, 10),
                tx.getAmount(),
                tx.getRecipientName() != null ? tx.getRecipientName() : "Unknown",
                tx.getStatus().name()
            ));
        }

        notificationService.sendMessage(whatsAppId, sb.toString());
    }

    private String resolveRecipientPhone(User user, String identifier) {
        // Check if it's a phone number
        String normalized = intentParsingService.normalizePhoneNumber(identifier);
        if (normalized != null) {
            return normalized;
        }

        // Try to find a contact by name
        return contactService.findContactPhoneByAlias(user.getId(), identifier);
    }

    private String resolveRecipientName(User user, String identifier, String phone) {
        // If identifier is not a phone, use it as the name
        if (!intentParsingService.isValidPhoneNumber(identifier)) {
            return identifier;
        }

        // Try to find contact name by phone
        return contactService.findContactNameByPhone(user.getId(), phone)
            .orElse(phone);
    }

    private String generateIdempotencyKey(UUID userId, String recipientPhone, BigDecimal amount) {
        String data = userId + ":" + recipientPhone + ":" + amount + ":" +
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
