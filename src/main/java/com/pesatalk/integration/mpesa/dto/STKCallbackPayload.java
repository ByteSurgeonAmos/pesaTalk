package com.pesatalk.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record STKCallbackPayload(
    @JsonProperty("Body") Body body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
        @JsonProperty("stkCallback") STKCallback stkCallback
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record STKCallback(
        @JsonProperty("MerchantRequestID") String merchantRequestID,
        @JsonProperty("CheckoutRequestID") String checkoutRequestID,
        @JsonProperty("ResultCode") Integer resultCode,
        @JsonProperty("ResultDesc") String resultDesc,
        @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackMetadata(
        @JsonProperty("Item") List<MetadataItem> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetadataItem(
        @JsonProperty("Name") String name,
        @JsonProperty("Value") Object value
    ) {}

    public boolean isSuccessful() {
        return body != null
            && body.stkCallback() != null
            && body.stkCallback().resultCode() == 0;
    }

    public String getCheckoutRequestId() {
        return Optional.ofNullable(body)
            .map(Body::stkCallback)
            .map(STKCallback::checkoutRequestID)
            .orElse(null);
    }

    public String getMerchantRequestId() {
        return Optional.ofNullable(body)
            .map(Body::stkCallback)
            .map(STKCallback::merchantRequestID)
            .orElse(null);
    }

    public Integer getResultCode() {
        return Optional.ofNullable(body)
            .map(Body::stkCallback)
            .map(STKCallback::resultCode)
            .orElse(null);
    }

    public String getResultDescription() {
        return Optional.ofNullable(body)
            .map(Body::stkCallback)
            .map(STKCallback::resultDesc)
            .orElse(null);
    }

    public String getMpesaReceiptNumber() {
        return getMetadataValue("MpesaReceiptNumber");
    }

    public String getTransactionDate() {
        return getMetadataValue("TransactionDate");
    }

    public String getPhoneNumber() {
        return getMetadataValue("PhoneNumber");
    }

    private String getMetadataValue(String key) {
        return Optional.ofNullable(body)
            .map(Body::stkCallback)
            .map(STKCallback::callbackMetadata)
            .map(CallbackMetadata::items)
            .orElse(List.of())
            .stream()
            .filter(item -> key.equals(item.name()))
            .findFirst()
            .map(item -> item.value() != null ? item.value().toString() : null)
            .orElse(null);
    }
}
