package com.pesatalk.service;

import com.pesatalk.exception.UserNotFoundException;
import com.pesatalk.model.User;
import com.pesatalk.model.enums.UserStatus;
import com.pesatalk.repository.UserRepository;
import com.pesatalk.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PhoneNumberUtil phoneNumberUtil;

    public UserService(UserRepository userRepository, PhoneNumberUtil phoneNumberUtil) {
        this.userRepository = userRepository;
        this.phoneNumberUtil = phoneNumberUtil;
    }

    @Transactional
    public User getOrCreateUser(String whatsAppId, String phoneNumber, String displayName) {
        return userRepository.findByWhatsAppId(whatsAppId)
            .map(user -> {
                // Update display name if provided and different
                if (displayName != null && !displayName.equals(user.getDisplayName())) {
                    user.setDisplayName(displayName);
                    return userRepository.save(user);
                }
                return user;
            })
            .orElseGet(() -> createNewUser(whatsAppId, phoneNumber, displayName));
    }

    private User createNewUser(String whatsAppId, String phoneNumber, String displayName) {
        String phoneHash = phoneNumberUtil.hashPhoneNumber(phoneNumber);

        User user = User.builder()
            .whatsAppId(whatsAppId)
            .phoneNumberHash(phoneHash)
            .displayName(displayName)
            .status(UserStatus.ACTIVE)
            .lastActivityAt(Instant.now())
            .dailyTransactionCount(0)
            .dailyTransactionAmount(0L)
            .build();

        User savedUser = userRepository.save(user);
        log.info("Created new user with id: {}", savedUser.getId());
        return savedUser;
    }

    public Optional<User> findByWhatsAppId(String whatsAppId) {
        return userRepository.findByWhatsAppId(whatsAppId);
    }

    public User getById(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));
    }

    @Transactional
    public void recordActivity(UUID userId) {
        userRepository.updateLastActivity(userId, Instant.now());
    }

    @Transactional
    public void incrementDailyStats(UUID userId, long amount) {
        userRepository.incrementDailyTransactionStats(userId, amount, Instant.now());
    }

    public boolean isUserActive(UUID userId) {
        return userRepository.isUserInStatus(userId, UserStatus.ACTIVE);
    }

    @Transactional
    public void suspendUser(UUID userId, String reason) {
        User user = getById(userId);
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        log.warn("User {} suspended: {}", userId, reason);
    }
}
