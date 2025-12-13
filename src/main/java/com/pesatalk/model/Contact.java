package com.pesatalk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.Instant;

@Entity
@Table(name = "contacts",
    indexes = {
        @Index(name = "idx_contacts_user_id", columnList = "user_id"),
        @Index(name = "idx_contacts_alias_lower", columnList = "alias_lowercase")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_contacts_user_alias", columnNames = {"user_id", "alias_lowercase"})
    }
)
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "alias", nullable = false, length = 50)
    private String alias;

    @Column(name = "alias_lowercase", nullable = false, length = 50)
    private String aliasLowercase;

    @Column(name = "phone_number_hash", nullable = false, length = 64)
    private String phoneNumberHash;

    @Column(name = "phone_number_encrypted", nullable = false)
    private String phoneNumberEncrypted;

    @Column(name = "is_favorite")
    @Builder.Default
    private Boolean isFavorite = false;

    @Column(name = "transaction_count")
    @Builder.Default
    private Integer transactionCount = 0;

    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;

    public void setAlias(String alias) {
        this.alias = alias;
        this.aliasLowercase = alias != null ? alias.toLowerCase() : null;
    }

    public void incrementTransactionCount() {
        this.transactionCount++;
        this.lastTransactionAt = Instant.now();
    }
}
