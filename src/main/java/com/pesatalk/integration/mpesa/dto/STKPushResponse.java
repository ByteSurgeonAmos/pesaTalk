package com.pesatalk.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record STKPushResponse(
    @JsonProperty("MerchantRequestID") String merchantRequestID,
    @JsonProperty("CheckoutRequestID") String checkoutRequestID,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription,
    @JsonProperty("CustomerMessage") String customerMessage,
    @JsonProperty("errorCode") String errorCode,
    @JsonProperty("errorMessage") String errorMessage
) {
    public boolean isSuccessful() {
        return "0".equals(responseCode);
    }

    public boolean hasError() {
        return errorCode != null || !"0".equals(responseCode);
    }

    public String getErrorDetails() {
        if (errorMessage != null) {
            return errorMessage;
        }
        if (responseDescription != null && !"0".equals(responseCode)) {
            return responseDescription;
        }
        return "Unknown error";
    }
}
