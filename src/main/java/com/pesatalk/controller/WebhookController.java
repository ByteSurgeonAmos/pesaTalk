package com.pesatalk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesatalk.exception.WebhookVerificationException;
import com.pesatalk.integration.whatsapp.dto.WhatsAppWebhookPayload;
import com.pesatalk.service.MessageProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final MessageProcessingService messageProcessingService;
    private final ObjectMapper objectMapper;
    private final Executor webhookExecutor;
    private final String verifyToken;
    private final String appSecret;

    public WebhookController(
        MessageProcessingService messageProcessingService,
        ObjectMapper objectMapper,
        @Qualifier("webhookExecutor") Executor webhookExecutor,
        @Value("${whatsapp.api.verify-token}") String verifyToken,
        @Value("${whatsapp.api.app-secret}") String appSecret
    ) {
        this.messageProcessingService = messageProcessingService;
        this.objectMapper = objectMapper;
        this.webhookExecutor = webhookExecutor;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
        @RequestParam("hub.mode") String mode,
        @RequestParam("hub.verify_token") String token,
        @RequestParam("hub.challenge") String challenge
    ) {
        log.info("Webhook verification request received");

        if (!"subscribe".equals(mode)) {
            log.warn("Invalid hub.mode: {}", mode);
            return ResponseEntity.badRequest().build();
        }

        if (!verifyToken.equals(token)) {
            log.warn("Invalid verify token received");
            throw WebhookVerificationException.invalidToken();
        }

        log.info("Webhook verified successfully");
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
        @RequestBody String rawPayload
    ) {
        // Verify signature
        verifySignature(signature, rawPayload);

        // Parse payload
        WhatsAppWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WhatsAppWebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest().build();
        }

        // Process asynchronously to respond within 20 seconds
        webhookExecutor.execute(() -> processPayload(payload));

        // Acknowledge immediately
        return ResponseEntity.ok().build();
    }

    private void verifySignature(String signature, String payload) {
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("App secret not configured, skipping signature verification");
            return;
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Missing webhook signature");
            throw WebhookVerificationException.missingSignature();
        }

        if (!signature.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Invalid signature format");
            throw WebhookVerificationException.invalidSignature();
        }

        String expectedSignature = signature.substring(SIGNATURE_PREFIX.length());
        String computedSignature = computeHmacSha256(payload, appSecret);


        if (!secureCompare(expectedSignature, computedSignature)) {
            log.warn("Webhook signature mismatch");
            throw WebhookVerificationException.invalidSignature();
        }

        log.debug("Webhook signature verified");
    }

    private String computeHmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC signature", e);
            throw new RuntimeException("Failed to compute webhook signature", e);
        }
    }

    private boolean secureCompare(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private void processPayload(WhatsAppWebhookPayload payload) {
        try {
            if (!"whatsapp_business_account".equals(payload.object())) {
                log.debug("Ignoring non-WhatsApp payload: {}", payload.object());
                return;
            }

            if (payload.entry() == null || payload.entry().isEmpty()) {
                log.debug("Empty payload entries");
                return;
            }

            for (var entry : payload.entry()) {
                if (entry.changes() == null) continue;

                for (var change : entry.changes()) {
                    if (change.value() == null) continue;

                    // Process messages
                    if (change.value().messages() != null) {
                        for (var message : change.value().messages()) {
                            processMessage(message, change.value());
                        }
                    }

                    // Process status updates
                    if (change.value().statuses() != null) {
                        for (var status : change.value().statuses()) {
                            processStatus(status);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload", e);
        }
    }

    private void processMessage(
        WhatsAppWebhookPayload.Message message,
        WhatsAppWebhookPayload.Value value
    ) {
        log.info("Processing message id={}, type={}", message.id(), message.type());

        String senderName = null;
        if (value.contacts() != null && !value.contacts().isEmpty()) {
            var contact = value.contacts().getFirst();
            if (contact.profile() != null) {
                senderName = contact.profile().name();
            }
        }

        messageProcessingService.processIncomingMessage(message, senderName);
    }

    private void processStatus(WhatsAppWebhookPayload.Status status) {
        log.debug("Message status update: id={}, status={}",
            status.id(), status.status());
    }
}
