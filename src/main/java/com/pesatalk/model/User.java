package com.pesatalk.model;

import com.pesatalk.model.enums.UserStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_phone_hash", columnList = "phone_number_hash", unique = true),
    @Index(name = "idx_users_whatsapp_id", columnList = "whatsapp_id", unique = true),
    @Index(name = "idx_users_status", columnList = "status")
})
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "phone_number_hash", nullable = false, unique = true, length = 64)
    private String phoneNumberHash;

    @Column(name = "whatsapp_id", nullable = false, unique = true, length = 50)
    private String whatsAppId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "daily_transaction_count")
    @Builder.Default
    private Integer dailyTransactionCount = 0;

    @Column(name = "daily_transaction_amount")
    @Builder.Default
    private Long dailyTransactionAmount = 0L;

    @Column(name = "last_transaction_date")
    private Instant lastTransactionDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Contact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    public void addContact(Contact contact) {
        contacts.add(contact);
        contact.setUser(this);
    }

    public void removeContact(Contact contact) {
        contacts.remove(contact);
        contact.setUser(null);
    }

    public void recordActivity() {
        this.lastActivityAt = Instant.now();
    }
}
