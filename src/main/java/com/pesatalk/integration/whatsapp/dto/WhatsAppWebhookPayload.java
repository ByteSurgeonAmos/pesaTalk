package com.pesatalk.integration.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppWebhookPayload(
    @JsonProperty("object") String object,
    @JsonProperty("entry") List<Entry> entry
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
        @JsonProperty("id") String id,
        @JsonProperty("changes") List<Change> changes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
        @JsonProperty("value") Value value,
        @JsonProperty("field") String field
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(
        @JsonProperty("messaging_product") String messagingProduct,
        @JsonProperty("metadata") Metadata metadata,
        @JsonProperty("contacts") List<Contact> contacts,
        @JsonProperty("messages") List<Message> messages,
        @JsonProperty("statuses") List<Status> statuses
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
        @JsonProperty("display_phone_number") String displayPhoneNumber,
        @JsonProperty("phone_number_id") String phoneNumberId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(
        @JsonProperty("profile") Profile profile,
        @JsonProperty("wa_id") String waId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
        @JsonProperty("name") String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        @JsonProperty("from") String from,
        @JsonProperty("id") String id,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("type") String type,
        @JsonProperty("text") TextContent text,
        @JsonProperty("interactive") Interactive interactive,
        @JsonProperty("button") Button button
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextContent(
        @JsonProperty("body") String body
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Interactive(
        @JsonProperty("type") String type,
        @JsonProperty("button_reply") ButtonReply buttonReply,
        @JsonProperty("list_reply") ListReply listReply
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ButtonReply(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListReply(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Button(
        @JsonProperty("payload") String payload,
        @JsonProperty("text") String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("recipient_id") String recipientId,
        @JsonProperty("conversation") Conversation conversation,
        @JsonProperty("pricing") Pricing pricing
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Conversation(
        @JsonProperty("id") String id,
        @JsonProperty("origin") Origin origin
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Origin(
        @JsonProperty("type") String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(
        @JsonProperty("billable") boolean billable,
        @JsonProperty("pricing_model") String pricingModel,
        @JsonProperty("category") String category
    ) {}
}
