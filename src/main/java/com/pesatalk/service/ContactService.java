package com.pesatalk.service;

import com.pesatalk.model.Contact;
import com.pesatalk.model.User;
import com.pesatalk.repository.ContactRepository;
import com.pesatalk.repository.UserRepository;
import com.pesatalk.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);
    private static final int MAX_CONTACTS_PER_USER = 100;

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final PhoneNumberUtil phoneNumberUtil;

    public ContactService(
        ContactRepository contactRepository,
        UserRepository userRepository,
        PhoneNumberUtil phoneNumberUtil
    ) {
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
        this.phoneNumberUtil = phoneNumberUtil;
    }

    @Transactional
    public Contact addContact(User user, String alias, String phoneNumber) {
        // Check contact limit
        long currentCount = contactRepository.countByUserId(user.getId());
        if (currentCount >= MAX_CONTACTS_PER_USER) {
            throw new IllegalStateException("Maximum number of contacts reached");
        }

        // Check for duplicate alias
        String aliasLowercase = alias.toLowerCase();
        if (contactRepository.existsByUserIdAndAliasLowercase(user.getId(), aliasLowercase)) {
            throw new IllegalArgumentException("A contact with this name already exists");
        }

        String normalizedPhone = phoneNumberUtil.normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            throw new IllegalArgumentException("Invalid phone number format");
        }

        Contact contact = Contact.builder()
            .user(user)
            .alias(alias)
            .aliasLowercase(aliasLowercase)
            .phoneNumberHash(phoneNumberUtil.hashPhoneNumber(normalizedPhone))
            .phoneNumberEncrypted(phoneNumberUtil.encryptPhoneNumber(normalizedPhone))
            .isFavorite(false)
            .transactionCount(0)
            .build();

        Contact saved = contactRepository.save(contact);
        log.info("Added contact {} for user {}", saved.getId(), user.getId());
        return saved;
    }

    public List<Contact> getUserContacts(UUID userId) {
        return contactRepository.findByUserIdOrderByTransactionCountDesc(userId);
    }

    public List<Contact> getFavoriteContacts(UUID userId) {
        return contactRepository.findByUserIdAndIsFavoriteTrue(userId);
    }

    public List<Contact> getTopContacts(UUID userId, int limit) {
        return contactRepository.findTopContactsByUserId(userId, limit);
    }

    public Optional<Contact> findByAlias(UUID userId, String alias) {
        return contactRepository.findByUserIdAndAliasIgnoreCase(userId, alias.toLowerCase());
    }

    public String findContactPhoneByAlias(UUID userId, String alias) {
        return findByAlias(userId, alias)
            .map(contact -> phoneNumberUtil.decryptPhoneNumber(contact.getPhoneNumberEncrypted()))
            .orElse(null);
    }

    public Optional<String> findContactNameByPhone(UUID userId, String phoneNumber) {
        String phoneHash = phoneNumberUtil.hashPhoneNumber(phoneNumber);

        List<Contact> contacts = contactRepository.findByUserIdOrderByTransactionCountDesc(userId);
        return contacts.stream()
            .filter(c -> phoneHash.equals(c.getPhoneNumberHash()))
            .findFirst()
            .map(Contact::getAlias);
    }

    @Transactional
    public void incrementContactTransactionCount(UUID userId, String phoneHash) {
        List<Contact> contacts = contactRepository.findByUserIdOrderByTransactionCountDesc(userId);
        contacts.stream()
            .filter(c -> phoneHash.equals(c.getPhoneNumberHash()))
            .findFirst()
            .ifPresent(contact -> {
                contact.incrementTransactionCount();
                contactRepository.save(contact);
            });
    }

    @Transactional
    public void deleteContact(UUID userId, String alias) {
        contactRepository.deleteByUserIdAndAliasLowercase(userId, alias.toLowerCase());
        log.info("Deleted contact {} for user {}", alias, userId);
    }

    @Transactional
    public void toggleFavorite(UUID userId, String alias) {
        findByAlias(userId, alias).ifPresent(contact -> {
            contact.setIsFavorite(!contact.getIsFavorite());
            contactRepository.save(contact);
        });
    }
}
