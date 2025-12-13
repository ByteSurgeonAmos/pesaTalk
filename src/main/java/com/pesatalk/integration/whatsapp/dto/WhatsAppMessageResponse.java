package com.pesatalk.integration.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppMessageResponse(
    @JsonProperty("messaging_product") String messagingProduct,
    @JsonProperty("contacts") List<Contact> contacts,
    @JsonProperty("messages") List<Message> messages
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(
        @JsonProperty("input") String input,
        @JsonProperty("wa_id") String waId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        @JsonProperty("id") String id
    ) {}

    public String getFirstMessageId() {
        if (messages != null && !messages.isEmpty()) {
            return messages.getFirst().id();
        }
        return null;
    }
}
