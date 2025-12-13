package com.pesatalk.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class PhoneNumberUtil {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberUtil.class);
    private static final String DEFAULT_REGION = "KE";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil;
    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;

    public PhoneNumberUtil(
        @Value("${encryption.phone-key:0123456789abcdef0123456789abcdef}") String encryptionKeyHex
    ) {
        this.phoneUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        this.secureRandom = new SecureRandom();

        // Convert hex key to bytes (32 bytes = 256 bits for AES-256)
        byte[] keyBytes = HexFormat.of().parseHex(encryptionKeyHex);
        this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }

        try {
            Phonenumber.PhoneNumber parsed = phoneUtil.parse(phoneNumber, DEFAULT_REGION);
            return phoneUtil.isValidNumber(parsed);
        } catch (NumberParseException e) {
            log.debug("Invalid phone number format: {}", e.getMessage());
            return false;
        }
    }

    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        try {
            Phonenumber.PhoneNumber parsed = phoneUtil.parse(phoneNumber, DEFAULT_REGION);

            if (!phoneUtil.isValidNumber(parsed)) {
                return null;
            }

            // Return in E.164 format without the + prefix
            String formatted = phoneUtil.format(parsed, PhoneNumberFormat.E164);
            return formatted.startsWith("+") ? formatted.substring(1) : formatted;

        } catch (NumberParseException e) {
            log.debug("Failed to parse phone number: {}", e.getMessage());
            return null;
        }
    }

    public String hashPhoneNumber(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            normalized = phoneNumber;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public String encryptPhoneNumber(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            normalized = phoneNumber;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));

            // Combine IV and ciphertext
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("Failed to encrypt phone number: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decryptPhoneNumber(String encryptedPhone) {
        if (encryptedPhone == null || encryptedPhone.isBlank()) {
            return null;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedPhone);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to decrypt phone number: {}", e.getMessage());
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "******";
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            normalized = phoneNumber;
        }

        // Show first 3 and last 3 digits
        int len = normalized.length();
        return normalized.substring(0, 3) + "****" + normalized.substring(len - 3);
    }
}
