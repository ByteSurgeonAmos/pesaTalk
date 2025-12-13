package com.pesatalk.integration.mpesa;

import com.pesatalk.exception.MPesaException;
import com.pesatalk.integration.mpesa.dto.MPesaAuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
public class MPesaAuthService {

    private static final Logger log = LoggerFactory.getLogger(MPesaAuthService.class);
    private static final String AUTH_ENDPOINT = "/oauth/v1/generate?grant_type=client_credentials";

    private final WebClient webClient;
    private final String consumerKey;
    private final String consumerSecret;

    public MPesaAuthService(
        WebClient.Builder webClientBuilder,
        @Value("${mpesa.api.base-url}") String baseUrl,
        @Value("${mpesa.api.consumer-key}") String consumerKey,
        @Value("${mpesa.api.consumer-secret}") String consumerSecret
    ) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
    }

    @Cacheable(value = "mpesa-tokens", unless = "#result == null")
    public String getAccessToken() {
        log.debug("Fetching new MPesa access token");

        String credentials = consumerKey + ":" + consumerSecret;
        String encodedCredentials = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        try {
            MPesaAuthResponse response = webClient.get()
                .uri(AUTH_ENDPOINT)
                .header("Authorization", "Basic " + encodedCredentials)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    res.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("MPesa auth error: status={}, body={}",
                                res.statusCode(), body);
                            return Mono.error(MPesaException.authenticationFailed(
                                new RuntimeException("Status: " + res.statusCode())
                            ));
                        })
                )
                .bodyToMono(MPesaAuthResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            if (response == null || response.accessToken() == null) {
                throw MPesaException.authenticationFailed(
                    new RuntimeException("Empty response from MPesa")
                );
            }

            log.info("Successfully obtained MPesa access token, expires in: {} seconds",
                response.expiresIn());
            return response.accessToken();

        } catch (Exception e) {
            log.error("Failed to obtain MPesa access token: {}", e.getMessage(), e);
            if (e instanceof MPesaException) {
                throw e;
            }
            throw MPesaException.authenticationFailed(e);
        }
    }
}
