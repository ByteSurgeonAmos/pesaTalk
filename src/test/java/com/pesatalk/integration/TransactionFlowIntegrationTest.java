package com.pesatalk.integration;

import com.pesatalk.model.Transaction;
import com.pesatalk.model.User;
import com.pesatalk.model.enums.TransactionStatus;
import com.pesatalk.model.enums.TransactionType;
import com.pesatalk.model.enums.UserStatus;
import com.pesatalk.repository.TransactionRepository;
import com.pesatalk.repository.UserRepository;
import com.pesatalk.service.TransactionService;
import com.pesatalk.dto.ParsedMessage;
import com.pesatalk.model.enums.Intent;
import com.pesatalk.model.enums.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionService transactionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
            .phoneNumberHash("hash123")
            .whatsAppId("254712345678")
            .displayName("Test User")
            .status(UserStatus.ACTIVE)
            .lastActivityAt(Instant.now())
            .build();
        testUser = userRepository.save(testUser);
    }

    @Nested
    @DisplayName("Send Money Flow")
    class SendMoneyFlowTests {

        @Test
        @DisplayName("Should create pending transaction for send money request")
        @Transactional
        void shouldCreatePendingTransaction() {
            ParsedMessage message = ParsedMessage.builder()
                .messageId("msg123")
                .senderWhatsAppId("254712345678")
                .senderPhoneNumber("254712345678")
                .senderName("Test User")
                .messageType(MessageType.TEXT)
                .rawContent("send 1500 to 0700000000")
                .intent(Intent.SEND_MONEY)
                .amount(new BigDecimal("1500"))
                .recipientIdentifier("0700000000")
                .build();

            transactionService.initiateSendMoney(testUser, message);

            var transactions = transactionRepository.findBySenderIdOrderByCreatedAtDesc(
                testUser.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)
            );

            assertThat(transactions.getContent()).hasSize(1);

            Transaction tx = transactions.getContent().get(0);
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING_CONFIRMATION);
            assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("1500"));
            assertThat(tx.getTransactionType()).isEqualTo(TransactionType.SEND_MONEY);
        }

        @Test
        @DisplayName("Should prevent duplicate transactions with same idempotency key")
        @Transactional
        void shouldPreventDuplicateTransactions() {
            ParsedMessage message = ParsedMessage.builder()
                .messageId("msg123")
                .senderWhatsAppId("254712345678")
                .senderPhoneNumber("254712345678")
                .senderName("Test User")
                .messageType(MessageType.TEXT)
                .rawContent("send 1500 to 0700000000")
                .intent(Intent.SEND_MONEY)
                .amount(new BigDecimal("1500"))
                .recipientIdentifier("0700000000")
                .build();

            // First request
            transactionService.initiateSendMoney(testUser, message);

            // Second identical request (should be blocked)
            transactionService.initiateSendMoney(testUser, message);

            var transactions = transactionRepository.findBySenderIdOrderByCreatedAtDesc(
                testUser.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)
            );

            // Should only have one transaction
            assertThat(transactions.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Transaction State Transitions")
    class TransactionStateTests {

        @Test
        @DisplayName("Should transition through valid states")
        @Transactional
        void shouldTransitionThroughValidStates() {
            Transaction tx = Transaction.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .sender(testUser)
                .transactionType(TransactionType.SEND_MONEY)
                .status(TransactionStatus.INITIATED)
                .amount(new BigDecimal("1000"))
                .recipientPhoneHash("hash456")
                .recipientPhoneEncrypted("encrypted")
                .build();

            tx = transactionRepository.save(tx);

            assertThat(tx.canTransitionTo(TransactionStatus.PENDING_CONFIRMATION)).isTrue();
            tx.transitionTo(TransactionStatus.PENDING_CONFIRMATION);

            assertThat(tx.canTransitionTo(TransactionStatus.CONFIRMED)).isTrue();
            tx.transitionTo(TransactionStatus.CONFIRMED);

            assertThat(tx.canTransitionTo(TransactionStatus.PROCESSING)).isTrue();
            tx.transitionTo(TransactionStatus.PROCESSING);

            assertThat(tx.canTransitionTo(TransactionStatus.STK_PUSHED)).isTrue();
            tx.transitionTo(TransactionStatus.STK_PUSHED);

            assertThat(tx.canTransitionTo(TransactionStatus.COMPLETED)).isTrue();
            tx.transitionTo(TransactionStatus.COMPLETED);

            assertThat(tx.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("Should prevent invalid state transitions")
        @Transactional
        void shouldPreventInvalidTransitions() {
            Transaction tx = Transaction.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .sender(testUser)
                .transactionType(TransactionType.SEND_MONEY)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("1000"))
                .recipientPhoneHash("hash456")
                .recipientPhoneEncrypted("encrypted")
                .build();

            tx = transactionRepository.save(tx);

            // Cannot transition from COMPLETED to PROCESSING
            assertThat(tx.canTransitionTo(TransactionStatus.PROCESSING)).isFalse();
            assertThat(tx.canTransitionTo(TransactionStatus.CANCELLED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Transaction Cancellation")
    class CancellationTests {

        @Test
        @DisplayName("Should cancel pending transaction")
        @Transactional
        void shouldCancelPendingTransaction() {
            Transaction tx = Transaction.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .sender(testUser)
                .transactionType(TransactionType.SEND_MONEY)
                .status(TransactionStatus.PENDING_CONFIRMATION)
                .amount(new BigDecimal("1000"))
                .recipientPhoneHash("hash456")
                .recipientPhoneEncrypted("encrypted")
                .confirmationExpiresAt(Instant.now().plusSeconds(300))
                .build();

            tx = transactionRepository.save(tx);

            transactionService.cancelTransaction(testUser, tx.getId().toString());

            Transaction updated = transactionRepository.findById(tx.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
            assertThat(updated.getFailureReason()).isEqualTo("Cancelled by user");
        }

        @Test
        @DisplayName("Should not cancel other user's transaction")
        @Transactional
        void shouldNotCancelOtherUsersTransaction() {
            User otherUser = User.builder()
                .phoneNumberHash("otherhash")
                .whatsAppId("254700000000")
                .displayName("Other User")
                .status(UserStatus.ACTIVE)
                .build();
            otherUser = userRepository.save(otherUser);

            Transaction tx = Transaction.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .sender(otherUser)
                .transactionType(TransactionType.SEND_MONEY)
                .status(TransactionStatus.PENDING_CONFIRMATION)
                .amount(new BigDecimal("1000"))
                .recipientPhoneHash("hash456")
                .recipientPhoneEncrypted("encrypted")
                .confirmationExpiresAt(Instant.now().plusSeconds(300))
                .build();

            tx = transactionRepository.save(tx);

            // Try to cancel as different user
            transactionService.cancelTransaction(testUser, tx.getId().toString());

            // Transaction should still be pending
            Transaction updated = transactionRepository.findById(tx.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.PENDING_CONFIRMATION);
        }
    }
}
