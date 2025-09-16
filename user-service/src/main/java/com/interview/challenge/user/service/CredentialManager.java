package com.interview.challenge.user.service;

import com.interview.challenge.user.constants.UserConstants;
import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CredentialManager {

    private static final Logger logger = LoggerFactory.getLogger(CredentialManager.class);

    @Autowired
    private StringEncryptor jasyptStringEncryptor;

    /**
     * Encrypt API credentials
     */
    public String encryptCredential(String credential) {
        return jasyptStringEncryptor.encrypt(credential);
    }

    /**
     * Decrypt API credential
     */
    public String decryptCredential(String encryptedCredential) {
        return jasyptStringEncryptor.decrypt(encryptedCredential);
    }

    /**
     * Encrypt both API key and secret
     */
    public Map<String, String> encryptCredentials(String apiKey, String apiSecret) {
        Map<String, String> encrypted = new HashMap<>();
        encrypted.put(UserConstants.KEY_API_KEY, encryptCredential(apiKey));
        encrypted.put(UserConstants.KEY_API_SECRET, encryptCredential(apiSecret));
        return encrypted;
    }

    /**
     * Decrypt both API key and secret
     */
    public Map<String, String> decryptCredentials(String encryptedApiKey, String encryptedApiSecret) {
        Map<String, String> decrypted = new HashMap<>();
        decrypted.put(UserConstants.KEY_API_KEY, decryptCredential(encryptedApiKey));
        decrypted.put(UserConstants.KEY_API_SECRET, decryptCredential(encryptedApiSecret));
        return decrypted;
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