package com.pesatalk.integration.mpesa;

import com.pesatalk.exception.MPesaException;
import com.pesatalk.integration.mpesa.dto.STKPushRequest;
import com.pesatalk.integration.mpesa.dto.STKPushResponse;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Component
public class MPesaClient {

    private static final Logger log = LoggerFactory.getLogger(MPesaClient.class);
    private static final String STK_PUSH_ENDPOINT = "/mpesa/stkpush/v1/processrequest";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WebClient webClient;
    private final MPesaAuthService authService;
    private final String shortcode;
    private final String passkey;
    private final String callbackUrl;

    public MPesaClient(
        WebClient.Builder webClientBuilder,
        MPesaAuthService authService,
        @Value("${mpesa.api.base-url}") String baseUrl,
        @Value("${mpesa.api.shortcode}") String shortcode,
        @Value("${mpesa.api.passkey}") String passkey,
        @Value("${mpesa.api.callback-url}") String callbackUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.authService = authService;
        this.shortcode = shortcode;
        this.passkey = passkey;
        this.callbackUrl = callbackUrl;
    }

    @CircuitBreaker(name = "mpesa", fallbackMethod = "stkPushFallback")
    @Retry(name = "mpesa")
    public STKPushResponse initiateSTKPush(
        String phoneNumber,
        BigDecimal amount,
        String accountReference,
        String description
    ) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String password = generatePassword(timestamp);

        // Ensure phone is in correct format (254XXXXXXXXX)
        String formattedPhone = formatPhoneNumber(phoneNumber);

        STKPushRequest request = STKPushRequest.builder()
            .businessShortCode(shortcode)
            .password(password)
            .timestamp(timestamp)
            .transactionType("CustomerPayBillOnline")
            .amount(amount.setScale(0, java.math.RoundingMode.DOWN).toString())
            .partyA(formattedPhone)
            .partyB(shortcode)
            .phoneNumber(formattedPhone)
            .callBackURL(callbackUrl + "/callback/mpesa/stk")
            .accountReference(truncate(accountReference, 12))
            .transactionDesc(truncate(description, 13))
            .build();

        log.info("Initiating STK Push for phone [REDACTED], amount: {}", amount);

        String accessToken = authService.getAccessToken();

        try {
            STKPushResponse response = webClient.post()
                .uri(STK_PUSH_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    res.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("MPesa STK Push error: status={}, body={}",
                                res.statusCode(), body);
                            return Mono.error(MPesaException.stkPushFailed(body));
                        })
                )
                .bodyToMono(STKPushResponse.class)
                .timeout(Duration.ofSeconds(45))
                .block();

            if (response == null) {
                throw MPesaException.stkPushFailed("Empty response from MPesa");
            }

            if (response.hasError()) {
                log.error("STK Push failed: {}", response.getErrorDetails());
                throw MPesaException.stkPushFailed(response.getErrorDetails());
            }

            log.info("STK Push initiated successfully: merchantRequestId={}, checkoutRequestId={}",
                response.merchantRequestID(), response.checkoutRequestID());

            return response;

        } catch (MPesaException e) {
            throw e;
        } catch (Exception e) {
            log.error("STK Push request failed: {}", e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw MPesaException.timeout();
            }
            throw MPesaException.stkPushFailed(e.getMessage());
        }
    }

    private String generatePassword(String timestamp) {
        String rawPassword = shortcode + passkey + timestamp;
        return Base64.getEncoder()
            .encodeToString(rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null) return null;

        // Remove any non-digit characters
        String cleaned = phone.replaceAll("\\D", "");

        // Handle different formats
        if (cleaned.startsWith("254")) {
            return cleaned;
        } else if (cleaned.startsWith("0")) {
            return "254" + cleaned.substring(1);
        } else if (cleaned.startsWith("7") || cleaned.startsWith("1")) {
            return "254" + cleaned;
        }

        return cleaned;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    @SuppressWarnings("unused")
    private STKPushResponse stkPushFallback(
        String phoneNumber,
        BigDecimal amount,
        String accountReference,
        String description,
        Throwable throwable
    ) {
        log.error("STK Push circuit breaker activated: {}", throwable.getMessage());
        throw MPesaException.serviceUnavailable();
    }
}
