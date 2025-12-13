package com.pesatalk.repository;

import com.pesatalk.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    @Query("""
        SELECT c FROM Contact c
        WHERE c.user.id = :userId
        AND c.aliasLowercase = :aliasLowercase
        """)
    Optional<Contact> findByUserIdAndAliasIgnoreCase(
        @Param("userId") UUID userId,
        @Param("aliasLowercase") String aliasLowercase
    );

    List<Contact> findByUserIdOrderByTransactionCountDesc(UUID userId);

    List<Contact> findByUserIdAndIsFavoriteTrue(UUID userId);

    @Query("""
        SELECT c FROM Contact c
        WHERE c.user.id = :userId
        AND (LOWER(c.alias) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        ORDER BY c.transactionCount DESC
        """)
    List<Contact> searchByAlias(
        @Param("userId") UUID userId,
        @Param("searchTerm") String searchTerm
    );

    @Query("""
        SELECT c FROM Contact c
        WHERE c.user.id = :userId
        ORDER BY c.transactionCount DESC
        LIMIT :limit
        """)
    List<Contact> findTopContactsByUserId(
        @Param("userId") UUID userId,
        @Param("limit") int limit
    );

    boolean existsByUserIdAndAliasLowercase(UUID userId, String aliasLowercase);

    long countByUserId(UUID userId);

    void deleteByUserIdAndAliasLowercase(UUID userId, String aliasLowercase);
}
