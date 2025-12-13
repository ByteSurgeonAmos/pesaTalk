package com.pesatalk.repository;

import com.pesatalk.model.User;
import com.pesatalk.model.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumberHash(String phoneNumberHash);

    Optional<User> findByWhatsAppId(String whatsAppId);

    boolean existsByPhoneNumberHash(String phoneNumberHash);

    boolean existsByWhatsAppId(String whatsAppId);

    @Modifying
    @Query("UPDATE User u SET u.lastActivityAt = :timestamp WHERE u.id = :userId")
    void updateLastActivity(@Param("userId") UUID userId, @Param("timestamp") Instant timestamp);

    @Modifying
    @Query("""
        UPDATE User u
        SET u.dailyTransactionCount = u.dailyTransactionCount + 1,
            u.dailyTransactionAmount = u.dailyTransactionAmount + :amount,
            u.lastTransactionDate = :timestamp
        WHERE u.id = :userId
        """)
    void incrementDailyTransactionStats(
        @Param("userId") UUID userId,
        @Param("amount") Long amount,
        @Param("timestamp") Instant timestamp
    );

    @Modifying
    @Query("""
        UPDATE User u
        SET u.dailyTransactionCount = 0,
            u.dailyTransactionAmount = 0
        WHERE u.lastTransactionDate < :cutoffDate
        """)
    int resetDailyLimitsBeforeDate(@Param("cutoffDate") Instant cutoffDate);

    @Query("""
        SELECT COUNT(u) > 0
        FROM User u
        WHERE u.id = :userId
        AND u.status = :status
        """)
    boolean isUserInStatus(@Param("userId") UUID userId, @Param("status") UserStatus status);
}
