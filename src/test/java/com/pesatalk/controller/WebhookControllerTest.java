package com.pesatalk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesatalk.config.SecurityConfig;
import com.pesatalk.service.MessageProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.Executor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "whatsapp.api.verify-token=test-verify-token",
    "whatsapp.api.app-secret=test-app-secret"
})
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageProcessingService messageProcessingService;

    @MockBean(name = "webhookExecutor")
    private Executor webhookExecutor;

    @Value("${whatsapp.api.verify-token}")
    private String verifyToken;

    @Value("${whatsapp.api.app-secret}")
    private String appSecret;

    @Nested
    @DisplayName("Webhook Verification (GET)")
    class WebhookVerificationTests {

        @Test
        @DisplayName("Should verify webhook with correct token")
        void shouldVerifyWebhookWithCorrectToken() throws Exception {
            String challenge = "test-challenge-123";

            mockMvc.perform(get("/webhook")
                    .param("hub.mode", "subscribe")
                    .param("hub.verify_token", "test-verify-token")
                    .param("hub.challenge", challenge))
                .andExpect(status().isOk())
                .andExpect(content().string(challenge));
        }

        @Test
        @DisplayName("Should reject webhook with incorrect token")
        void shouldRejectWebhookWithIncorrectToken() throws Exception {
            mockMvc.perform(get("/webhook")
                    .param("hub.mode", "subscribe")
                    .param("hub.verify_token", "wrong-token")
                    .param("hub.challenge", "test-challenge"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject webhook with incorrect mode")
        void shouldRejectWebhookWithIncorrectMode() throws Exception {
            mockMvc.perform(get("/webhook")
                    .param("hub.mode", "unsubscribe")
                    .param("hub.verify_token", "test-verify-token")
                    .param("hub.challenge", "test-challenge"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Webhook Message Handling (POST)")
    class WebhookMessageHandlingTests {

        @Test
        @DisplayName("Should accept valid webhook with correct signature")
        void shouldAcceptValidWebhook() throws Exception {
            String payload = """
                {
                    "object": "whatsapp_business_account",
                    "entry": [{
                        "id": "123456789",
                        "changes": [{
                            "value": {
                                "messaging_product": "whatsapp",
                                "metadata": {
                                    "display_phone_number": "254700000000",
                                    "phone_number_id": "123456789"
                                },
                                "contacts": [{
                                    "profile": {"name": "Test User"},
                                    "wa_id": "254712345678"
                                }],
                                "messages": [{
                                    "from": "254712345678",
                                    "id": "msg123",
                                    "timestamp": "1234567890",
                                    "type": "text",
                                    "text": {"body": "Hello"}
                                }]
                            },
                            "field": "messages"
                        }]
                    }]
                }
                """;

            String signature = "sha256=" + computeHmacSha256(payload, appSecret);

            mockMvc.perform(post("/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hub-Signature-256", signature)
                    .content(payload))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should reject webhook with invalid signature")
        void shouldRejectWebhookWithInvalidSignature() throws Exception {
            String payload = """
                {
                    "object": "whatsapp_business_account",
                    "entry": []
                }
                """;

            mockMvc.perform(post("/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hub-Signature-256", "sha256=invalidsignature")
                    .content(payload))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject webhook without signature")
        void shouldRejectWebhookWithoutSignature() throws Exception {
            String payload = """
                {
                    "object": "whatsapp_business_account",
                    "entry": []
                }
                """;

            mockMvc.perform(post("/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isUnauthorized());
        }
    }

    private String computeHmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }
}
