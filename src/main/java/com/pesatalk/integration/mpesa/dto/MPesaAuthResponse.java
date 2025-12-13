package com.pesatalk.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MPesaAuthResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") String expiresIn
) {
    public long getExpiresInSeconds() {
        try {
            return Long.parseLong(expiresIn);
        } catch (NumberFormatException e) {
            return 3599L;
        }
    }
}
