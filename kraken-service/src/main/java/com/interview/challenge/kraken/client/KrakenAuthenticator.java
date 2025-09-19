package com.interview.challenge.kraken.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Kraken API Authenticator
 *
 * Implements the Kraken Futures API authentication mechanism:
 * 1. Create SHA-256 hash of (postData + nonce + path)
 * 2. Create HMAC-SHA512 of (path + SHA256 hash) using API secret
 * 3. Base64 encode the result
 */
@Slf4j
@Component
public class KrakenAuthenticator {

    private static final String HMAC_SHA512 = "HmacSHA512";
    private static final String SHA256 = "SHA-256";

    /**
     * Generate authentication header for Kraken API
     *
     * @param apiSecret Base64 encoded API secret
     * @param path API endpoint path (e.g., "/derivatives/api/v3/sendorder")
     * @param nonce Unique incrementing value (timestamp in milliseconds)
     * @param postData POST request body (empty string for GET requests)
     * @return Base64 encoded authentication string
     */
    public String generateAuthent(String apiSecret, String path, String nonce, String postData) {
        try {
            // Step 1: Create message = postData + nonce + path
            String message = postData + nonce + path;

            // Step 2: SHA-256 hash of the message
            byte[] sha256Hash = sha256(message);

            // Step 3: Combine path bytes with SHA-256 hash
            byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
            byte[] dataToSign = new byte[pathBytes.length + sha256Hash.length];
            System.arraycopy(pathBytes, 0, dataToSign, 0, pathBytes.length);
            System.arraycopy(sha256Hash, 0, dataToSign, pathBytes.length, sha256Hash.length);

            // Step 4: HMAC-SHA512 using the API secret
            byte[] hmacResult = hmacSha512(dataToSign, Base64.decodeBase64(apiSecret));

            // Step 5: Base64 encode the result
            String authent = Base64.encodeBase64String(hmacResult);

            log.debug("Generated Authent for path: {}, nonce: {}", path, nonce);

            return authent;

        } catch (Exception e) {
            log.error("Error generating Kraken authentication: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Kraken authentication", e);
        }
    }

    /**
     * Generate a nonce (unique value) for the request
     * Using current timestamp in milliseconds
     */
    public String generateNonce() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * Create SHA-256 hash
     */
    private byte[] sha256(String data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(SHA256);
        return digest.digest(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create HMAC-SHA512
     */
    private byte[] hmacSha512(byte[] data, byte[] secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA512);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret, HMAC_SHA512);
        mac.init(secretKeySpec);
        return mac.doFinal(data);
    }

    /**
     * Validate API credentials format
     */
    public boolean validateCredentials(String apiKey, String apiSecret) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("Invalid API key: null or empty");
            return false;
        }

        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            log.warn("Invalid API secret: null or empty");
            return false;
        }

        // Kraken API keys are typically base64 encoded
        try {
            Base64.decodeBase64(apiSecret);
            return true;
        } catch (Exception e) {
            log.warn("Invalid API secret format: not valid base64");
            return false;
        }
    }
}