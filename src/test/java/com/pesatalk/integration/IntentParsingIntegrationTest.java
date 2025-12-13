package com.pesatalk.integration;

import com.pesatalk.model.enums.Intent;
import com.pesatalk.service.intent.IntentParsingOrchestrator;
import com.pesatalk.service.intent.ParsedIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IntentParsingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IntentParsingOrchestrator orchestrator;

    @Nested
    @DisplayName("Send Money Parsing")
    class SendMoneyTests {

        @ParameterizedTest
        @CsvSource({
            "'send 1500 to john', SEND_MONEY, 1500, john",
            "'transfer 2000 to 0712345678', SEND_MONEY, 2000, 0712345678",
            "'pay 500 for mama', SEND_MONEY, 500, mama",
            "'send kes 1,000 to brother', SEND_MONEY, 1000, brother",
            "'tuma 300 kwa jane', SEND_MONEY, 300, jane"
        })
        @DisplayName("Should parse send money commands")
        void shouldParseSendMoneyCommands(String input, Intent expectedIntent, String expectedAmount, String expectedRecipient) {
            ParsedIntent result = orchestrator.parseText(input);

            assertThat(result.intent()).isEqualTo(expectedIntent);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal(expectedAmount));
            assertThat(result.recipientIdentifier()).isEqualToIgnoringCase(expectedRecipient);
            assertThat(result.confidence()).isGreaterThan(0.5);
        }
    }

    @Nested
    @DisplayName("Airtime Parsing")
    class AirtimeTests {

        @ParameterizedTest
        @CsvSource({
            "'buy airtime 100', BUY_AIRTIME, 100",
            "'airtime 50', BUY_AIRTIME, 50",
            "'top up 200', BUY_AIRTIME, 200",
            "'recharge 500', BUY_AIRTIME, 500"
        })
        @DisplayName("Should parse airtime commands")
        void shouldParseAirtimeCommands(String input, Intent expectedIntent, String expectedAmount) {
            ParsedIntent result = orchestrator.parseText(input);

            assertThat(result.intent()).isEqualTo(expectedIntent);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal(expectedAmount));
            assertThat(result.confidence()).isGreaterThan(0.5);
        }
    }

    @Nested
    @DisplayName("Keyword Intents")
    class KeywordTests {

        @Test
        @DisplayName("Should detect balance check intent")
        void shouldDetectBalanceIntent() {
            ParsedIntent result = orchestrator.parseText("check my balance");
            assertThat(result.intent()).isEqualTo(Intent.CHECK_BALANCE);
        }

        @Test
        @DisplayName("Should detect history intent")
        void shouldDetectHistoryIntent() {
            ParsedIntent result = orchestrator.parseText("show my transactions");
            assertThat(result.intent()).isEqualTo(Intent.TRANSACTION_HISTORY);
        }

        @Test
        @DisplayName("Should detect help intent")
        void shouldDetectHelpIntent() {
            ParsedIntent result = orchestrator.parseText("help");
            assertThat(result.intent()).isEqualTo(Intent.HELP);
        }
    }

    @Nested
    @DisplayName("Button Responses")
    class ButtonResponseTests {

        @Test
        @DisplayName("Should parse confirm button response")
        void shouldParseConfirmButton() {
            ParsedIntent result = orchestrator.parseButtonResponse("confirm_abc123-def456");

            assertThat(result.intent()).isEqualTo(Intent.CONFIRM_TRANSACTION);
            assertThat(result.confidence()).isEqualTo(1.0);
            assertThat(result.metadata()).containsKey("transactionId");
        }

        @Test
        @DisplayName("Should parse cancel button response")
        void shouldParseCancelButton() {
            ParsedIntent result = orchestrator.parseButtonResponse("cancel_abc123-def456");

            assertThat(result.intent()).isEqualTo(Intent.CANCEL_TRANSACTION);
            assertThat(result.confidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Confidence Scoring")
    class ConfidenceTests {

        @Test
        @DisplayName("Explicit commands should have high confidence")
        void explicitCommandsShouldHaveHighConfidence() {
            ParsedIntent result = orchestrator.parseText("send 1500 to 0712345678");

            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.8);
        }

        @Test
        @DisplayName("Unknown input should have zero confidence")
        void unknownInputShouldHaveZeroConfidence() {
            ParsedIntent result = orchestrator.parseText("what is the weather like today");

            assertThat(result.intent()).isEqualTo(Intent.UNKNOWN);
            assertThat(result.confidence()).isEqualTo(0.0);
        }
    }
}
