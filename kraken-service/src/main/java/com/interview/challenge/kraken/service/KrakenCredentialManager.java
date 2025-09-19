package com.interview.challenge.kraken.service;

import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages encryption and decryption of Kraken API credentials
 * Ensures credentials are stored encrypted in MongoDB and decrypted only when needed
 */
@Component
public class KrakenCredentialManager {

    private static final Logger logger = LoggerFactory.getLogger(KrakenCredentialManager.class);

    @Autowired
    private StringEncryptor jasyptStringEncryptor;

    /**
     * Encrypt API credential
     */
    public String encryptCredential(String credential) {
        if (credential == null || credential.isEmpty()) {
            return credential;
        }
        try {
            String encrypted = jasyptStringEncryptor.encrypt(credential);
            logger.debug("🔐 Credential encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            logger.error("❌ Failed to encrypt credential: {}", e.getMessage());
            throw new RuntimeException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypt API credential
     */
    public String decryptCredential(String encryptedCredential) {
        if (encryptedCredential == null || encryptedCredential.isEmpty()) {
            return encryptedCredential;
        }
        try {
            String decrypted = jasyptStringEncryptor.decrypt(encryptedCredential);
            logger.debug("🔓 Credential decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            logger.error("❌ Failed to decrypt credential: {}", e.getMessage());
            throw new RuntimeException("Failed to decrypt credential", e);
        }
    }

    /**
     * Encrypt both API key and secret
     */
    public Map<String, String> encryptCredentials(String apiKey, String apiSecret) {
        Map<String, String> encrypted = new HashMap<>();
        encrypted.put("apiKey", encryptCredential(apiKey));
        encrypted.put("apiSecret", encryptCredential(apiSecret));
        logger.debug("🔐 Both credentials encrypted");
        return encrypted;
    }

    /**
     * Decrypt both API key and secret
     */
    public Map<String, String> decryptCredentials(String encryptedApiKey, String encryptedApiSecret) {
        Map<String, String> decrypted = new HashMap<>();
        decrypted.put("apiKey", decryptCredential(encryptedApiKey));
        decrypted.put("apiSecret", decryptCredential(encryptedApiSecret));
        logger.debug("🔓 Both credentials decrypted");
        return decrypted;
    }

    /**
     * Check if a string is already encrypted (base64 format check)
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // Jasypt encrypted strings are base64 encoded and typically longer
        // than normal API keys/secrets
        return value.matches("^[A-Za-z0-9+/]+=*$") && value.length() > 60;
    }

    /**
     * Validate credentials are not empty
     */
    public boolean validateCredentials(String apiKey, String apiSecret) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("API key is empty or null");
            return false;
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            logger.warn("API secret is empty or null");
            return false;
        }
        return true;
    }
}