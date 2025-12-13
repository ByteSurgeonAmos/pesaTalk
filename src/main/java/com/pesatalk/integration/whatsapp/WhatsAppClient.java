package com.pesatalk.integration.whatsapp;

import com.pesatalk.integration.whatsapp.dto.WhatsAppMessageRequest;
import com.pesatalk.integration.whatsapp.dto.WhatsAppMessageResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final WebClient webClient;
    private final String phoneNumberId;

    public WhatsAppClient(
        WebClient.Builder webClientBuilder,
        @Value("${whatsapp.api.base-url}") String baseUrl,
        @Value("${whatsapp.api.phone-number-id}") String phoneNumberId,
        @Value("${whatsapp.api.access-token}") String accessToken
    ) {
        this.phoneNumberId = phoneNumberId;
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + accessToken)
            .build();
    }

    @CircuitBreaker(name = "whatsapp", fallbackMethod = "sendMessageFallback")
    @Retry(name = "whatsapp")
    public WhatsAppMessageResponse sendTextMessage(String recipientId, String message) {
        WhatsAppMessageRequest request = WhatsAppMessageRequest.textMessage(recipientId, message);
        return sendMessage(request);
    }

    @CircuitBreaker(name = "whatsapp", fallbackMethod = "sendMessageFallback")
    @Retry(name = "whatsapp")
    public WhatsAppMessageResponse sendInteractiveButtons(
        String recipientId,
        String bodyText,
        List<WhatsAppMessageRequest.Button> buttons
    ) {
        WhatsAppMessageRequest request = WhatsAppMessageRequest.interactiveButtons(
            recipientId,
            bodyText,
            buttons
        );
        return sendMessage(request);
    }

    @CircuitBreaker(name = "whatsapp", fallbackMethod = "sendMessageFallback")
    @Retry(name = "whatsapp")
    public WhatsAppMessageResponse sendInteractiveList(
        String recipientId,
        String bodyText,
        String buttonText,
        List<WhatsAppMessageRequest.Section> sections
    ) {
        WhatsAppMessageRequest request = WhatsAppMessageRequest.interactiveList(
            recipientId,
            bodyText,
            buttonText,
            sections
        );
        return sendMessage(request);
    }

    private WhatsAppMessageResponse sendMessage(WhatsAppMessageRequest request) {
        String endpoint = "/" + phoneNumberId + "/messages";

        log.debug("Sending WhatsApp message to: {}", request.to());

        return webClient.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("WhatsApp API error: status={}, body={}",
                            response.statusCode(), body);
                        return Mono.error(new RuntimeException(
                            "WhatsApp API error: " + response.statusCode()
                        ));
                    })
            )
            .bodyToMono(WhatsAppMessageResponse.class)
            .timeout(Duration.ofSeconds(30))
            .block();
    }

    @SuppressWarnings("unused")
    private WhatsAppMessageResponse sendMessageFallback(
        String recipientId,
        String message,
        Throwable throwable
    ) {
        log.error("WhatsApp message sending failed, circuit breaker activated: {}",
            throwable.getMessage());
        return null;
    }

    @SuppressWarnings("unused")
    private WhatsAppMessageResponse sendMessageFallback(
        String recipientId,
        String bodyText,
        List<WhatsAppMessageRequest.Button> buttons,
        Throwable throwable
    ) {
        log.error("WhatsApp interactive message sending failed, circuit breaker activated: {}",
            throwable.getMessage());
        return null;
    }

    @SuppressWarnings("unused")
    private WhatsAppMessageResponse sendMessageFallback(
        String recipientId,
        String bodyText,
        String buttonText,
        List<WhatsAppMessageRequest.Section> sections,
        Throwable throwable
    ) {
        log.error("WhatsApp list message sending failed, circuit breaker activated: {}",
            throwable.getMessage());
        return null;
    }
}
