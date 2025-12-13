package com.pesatalk.integration.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WhatsAppMessageRequest(
    @JsonProperty("messaging_product") String messagingProduct,
    @JsonProperty("recipient_type") String recipientType,
    @JsonProperty("to") String to,
    @JsonProperty("type") String type,
    @JsonProperty("text") TextBody text,
    @JsonProperty("interactive") Interactive interactive
) {
    public static WhatsAppMessageRequest textMessage(String to, String message) {
        return new WhatsAppMessageRequest(
            "whatsapp",
            "individual",
            to,
            "text",
            new TextBody(false, message),
            null
        );
    }

    public static WhatsAppMessageRequest interactiveButtons(
        String to,
        String bodyText,
        List<Button> buttons
    ) {
        return new WhatsAppMessageRequest(
            "whatsapp",
            "individual",
            to,
            "interactive",
            null,
            new Interactive(
                "button",
                new Body(bodyText),
                null,
                new Action(buttons, null)
            )
        );
    }

    public static WhatsAppMessageRequest interactiveList(
        String to,
        String bodyText,
        String buttonText,
        List<Section> sections
    ) {
        return new WhatsAppMessageRequest(
            "whatsapp",
            "individual",
            to,
            "interactive",
            null,
            new Interactive(
                "list",
                new Body(bodyText),
                null,
                new Action(null, new ActionList(buttonText, sections))
            )
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TextBody(
        @JsonProperty("preview_url") boolean previewUrl,
        @JsonProperty("body") String body
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Interactive(
        @JsonProperty("type") String type,
        @JsonProperty("body") Body body,
        @JsonProperty("header") Header header,
        @JsonProperty("action") Action action
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(
        @JsonProperty("text") String text
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Header(
        @JsonProperty("type") String type,
        @JsonProperty("text") String text
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Action(
        @JsonProperty("buttons") List<Button> buttons,
        @JsonProperty("list") ActionList list
    ) {
        public Action(List<Button> buttons, ActionList list) {
            this.buttons = buttons;
            this.list = list;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActionList(
        @JsonProperty("button") String button,
        @JsonProperty("sections") List<Section> sections
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Button(
        @JsonProperty("type") String type,
        @JsonProperty("reply") Reply reply
    ) {
        public static Button of(String id, String title) {
            return new Button("reply", new Reply(id, title));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reply(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(
        @JsonProperty("title") String title,
        @JsonProperty("rows") List<Row> rows
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description
    ) {}
}
