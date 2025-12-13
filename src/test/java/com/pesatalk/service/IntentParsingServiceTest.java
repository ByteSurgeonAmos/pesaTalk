package com.pesatalk.service;

import com.pesatalk.model.enums.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IntentParsingServiceTest {

    private IntentParsingService intentParsingService;

    @BeforeEach
    void setUp() {
        intentParsingService = new IntentParsingService();
    }

    @Nested
    @DisplayName("Send Money Intent Parsing")
    class SendMoneyTests {

        @ParameterizedTest
        @CsvSource({
            "'send 1500 to john', 1500, john",
            "'Send 1500 to John', 1500, John",
            "'SEND 1500 TO JOHN', 1500, JOHN",
            "'send kes 1500 to john', 1500, john",
            "'send KES 1,500 to john', 1500, john",
            "'transfer 500 to mama', 500, mama",
            "'pay 2000 to 0712345678', 2000, 0712345678",
            "'send 100.50 to jane', 100.50, jane"
        })
        @DisplayName("Should parse send money commands correctly")
        void shouldParseSendMoneyCommands(String input, String expectedAmount, String expectedRecipient) {
            var result = intentParsingService.parseIntent(input);

            assertThat(result.intent()).isEqualTo(Intent.SEND_MONEY);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal(expectedAmount));
            assertThat(result.recipientIdentifier()).isEqualTo(expectedRecipient);
        }

        @Test
        @DisplayName("Should handle amount with commas")
        void shouldHandleAmountWithCommas() {
            var result = intentParsingService.parseIntent("send 10,000 to john");

            assertThat(result.intent()).isEqualTo(Intent.SEND_MONEY);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(result.recipientIdentifier()).isEqualTo("john");
        }

        @Test
        @DisplayName("Should handle multi-word recipient names")
        void shouldHandleMultiWordRecipientNames() {
            var result = intentParsingService.parseIntent("send 500 to John Doe");

            assertThat(result.intent()).isEqualTo(Intent.SEND_MONEY);
            assertThat(result.recipientIdentifier()).isEqualTo("John Doe");
        }
    }

    @Nested
    @DisplayName("Airtime Intent Parsing")
    class AirtimeTests {

        @ParameterizedTest
        @CsvSource({
            "'buy airtime 100', 100",
            "'airtime 200', 200",
            "'buy airtime kes 50', 50"
        })
        @DisplayName("Should parse airtime commands correctly")
        void shouldParseAirtimeCommands(String input, String expectedAmount) {
            var result = intentParsingService.parseIntent(input);

            assertThat(result.intent()).isEqualTo(Intent.BUY_AIRTIME);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal(expectedAmount));
        }
    }

    @Nested
    @DisplayName("Other Intents")
    class OtherIntentsTests {

        @ParameterizedTest
        @ValueSource(strings = {"balance", "check balance", "my balance", "how much do I have"})
        @DisplayName("Should detect balance check intent")
        void shouldDetectBalanceIntent(String input) {
            var result = intentParsingService.parseIntent(input);
            assertThat(result.intent()).isEqualTo(Intent.CHECK_BALANCE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"history", "transactions", "my transactions", "recent transactions"})
        @DisplayName("Should detect history intent")
        void shouldDetectHistoryIntent(String input) {
            var result = intentParsingService.parseIntent(input);
            assertThat(result.intent()).isEqualTo(Intent.TRANSACTION_HISTORY);
        }

        @ParameterizedTest
        @ValueSource(strings = {"help", "menu", "what can you do", "show me commands"})
        @DisplayName("Should detect help intent")
        void shouldDetectHelpIntent(String input) {
            var result = intentParsingService.parseIntent(input);
            assertThat(result.intent()).isEqualTo(Intent.HELP);
        }

        @ParameterizedTest
        @ValueSource(strings = {"hello", "random text", "what is this", ""})
        @DisplayName("Should return unknown for unrecognized input")
        void shouldReturnUnknownForUnrecognizedInput(String input) {
            var result = intentParsingService.parseIntent(input);
            assertThat(result.intent()).isEqualTo(Intent.UNKNOWN);
        }

        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNullInput() {
            var result = intentParsingService.parseIntent(null);
            assertThat(result.intent()).isEqualTo(Intent.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Phone Number Validation")
    class PhoneNumberValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {"0712345678", "0700000000", "254712345678", "+254712345678"})
        @DisplayName("Should validate correct Kenyan phone numbers")
        void shouldValidateCorrectPhoneNumbers(String phone) {
            assertThat(intentParsingService.isValidPhoneNumber(phone)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"123", "john", "12345678901234", "", "abc"})
        @DisplayName("Should reject invalid phone numbers")
        void shouldRejectInvalidPhoneNumbers(String phone) {
            assertThat(intentParsingService.isValidPhoneNumber(phone)).isFalse();
        }

        @Test
        @DisplayName("Should handle null phone number")
        void shouldHandleNullPhoneNumber() {
            assertThat(intentParsingService.isValidPhoneNumber(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Phone Number Normalization")
    class PhoneNumberNormalizationTests {

        @ParameterizedTest
        @CsvSource({
            "0712345678, 254712345678",
            "712345678, 254712345678",
            "254712345678, 254712345678",
            "+254712345678, 254712345678"
        })
        @DisplayName("Should normalize phone numbers to international format")
        void shouldNormalizePhoneNumbers(String input, String expected) {
            assertThat(intentParsingService.normalizePhoneNumber(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should return null for invalid phone numbers")
        void shouldReturnNullForInvalidPhoneNumbers() {
            assertThat(intentParsingService.normalizePhoneNumber("invalid")).isNull();
        }
    }
}
