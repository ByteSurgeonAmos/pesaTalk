package com.pesatalk.repository;

import com.pesatalk.model.Transaction;
import com.pesatalk.model.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByCheckoutRequestId(String checkoutRequestId);

    Optional<Transaction> findByMpesaReceiptNumber(String mpesaReceiptNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdWithLock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.checkoutRequestId = :checkoutRequestId")
    Optional<Transaction> findByCheckoutRequestIdWithLock(@Param("checkoutRequestId") String checkoutRequestId);

    Page<Transaction> findBySenderIdOrderByCreatedAtDesc(UUID senderId, Pageable pageable);

    List<Transaction> findBySenderIdAndStatusOrderByCreatedAtDesc(
        UUID senderId,
        TransactionStatus status
    );

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.sender.id = :senderId
        AND t.status IN :statuses
        ORDER BY t.createdAt DESC
        """)
    List<Transaction> findBySenderIdAndStatusIn(
        @Param("senderId") UUID senderId,
        @Param("statuses") List<TransactionStatus> statuses
    );

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.status = :status
        AND t.confirmationExpiresAt < :now
        """)
    List<Transaction> findExpiredPendingConfirmations(
        @Param("status") TransactionStatus status,
        @Param("now") Instant now
    );

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.status = :status
        AND t.stkPushedAt < :cutoff
        """)
    List<Transaction> findStaleProcessingTransactions(
        @Param("status") TransactionStatus status,
        @Param("cutoff") Instant cutoff
    );

    @Modifying
    @Query("""
        UPDATE Transaction t
        SET t.status = :newStatus, t.failedAt = :now, t.failureReason = :reason
        WHERE t.id IN :ids
        AND t.status = :currentStatus
        """)
    int bulkUpdateExpiredTransactions(
        @Param("ids") List<UUID> ids,
        @Param("currentStatus") TransactionStatus currentStatus,
        @Param("newStatus") TransactionStatus newStatus,
        @Param("now") Instant now,
        @Param("reason") String reason
    );

    @Query("""
        SELECT COUNT(t)
        FROM Transaction t
        WHERE t.sender.id = :senderId
        AND t.createdAt >= :startOfDay
        AND t.status NOT IN :excludedStatuses
        """)
    long countDailyTransactions(
        @Param("senderId") UUID senderId,
        @Param("startOfDay") Instant startOfDay,
        @Param("excludedStatuses") List<TransactionStatus> excludedStatuses
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.sender.id = :senderId
        AND t.createdAt >= :startOfDay
        AND t.status NOT IN :excludedStatuses
        """)
    java.math.BigDecimal sumDailyTransactionAmount(
        @Param("senderId") UUID senderId,
        @Param("startOfDay") Instant startOfDay,
        @Param("excludedStatuses") List<TransactionStatus> excludedStatuses
    );

    boolean existsByIdempotencyKey(String idempotencyKey);
}
