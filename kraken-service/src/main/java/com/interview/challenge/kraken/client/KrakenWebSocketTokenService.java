package com.interview.challenge.kraken.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to obtain WebSocket authentication tokens from Kraken REST API
 *
 * Kraken WebSocket requires a token obtained via REST API endpoint:
 * /0/private/GetWebSocketsToken
 *
 * The token is valid for 15 minutes to establish connection
 * Once connected, it doesn't expire
 */
@Slf4j
@Service
public class KrakenWebSocketTokenService {

    private static final String TOKEN_ENDPOINT = "/0/private/GetWebSocketsToken";
    private static final String API_VERSION = "0";

    @Value("${kraken.api.base-url:https://api.kraken.com}")
    private String krakenBaseUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Get WebSocket authentication token for a client
     *
     * @param apiKey The API key (must have WebSocket permission)
     * @param apiSecret The API secret
     * @return WebSocket token valid for 15 minutes
     */
    public String getWebSocketToken(String apiKey, String apiSecret) {
        try {
            log.info("🎫 Requesting WebSocket token from Kraken REST API");

            String url = krakenBaseUrl + TOKEN_ENDPOINT;
            String nonce = String.valueOf(System.currentTimeMillis());

            // Prepare request
            Map<String, String> params = new HashMap<>();
            params.put("nonce", nonce);

            // Create signature
            String postData = "nonce=" + nonce;
            String signature = createSignature(TOKEN_ENDPOINT, nonce, postData, apiSecret);

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("API-Key", apiKey);
            headers.set("API-Sign", signature);

            // Create request
            HttpEntity<String> request = new HttpEntity<>(postData, headers);

            // Make REST call
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );

            // Parse response
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);

            // Check for errors
            if (responseBody.containsKey("error") && !((List) responseBody.get("error")).isEmpty()) {
                String error = responseBody.get("error").toString();
                log.error("❌ Kraken API error: {}", error);
                throw new RuntimeException("Failed to get WebSocket token: " + error);
            }

            // Extract token
            Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
            String token = (String) result.get("token");

            if (token == null || token.isEmpty()) {
                throw new RuntimeException("No token in response");
            }

            log.info("✅ WebSocket token obtained successfully (valid for 15 minutes)");
            return token;

        } catch (Exception e) {
            log.error("❌ Failed to get WebSocket token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get WebSocket token", e);
        }
    }

    /**
     * Create HMAC-SHA512 signature for Kraken API
     *
     * Kraken signature = HMAC-SHA512 of (URI path + SHA256(nonce + POST data)) using base64 decoded secret
     */
    private String createSignature(String path, String nonce, String postData, String apiSecret) throws Exception {
        // Step 1: SHA256 hash of (nonce + postdata)
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update((nonce + postData).getBytes());
        byte[] hash = sha256.digest();

        // Step 2: Combine path + hash
        byte[] pathBytes = path.getBytes();
        byte[] message = new byte[pathBytes.length + hash.length];
        System.arraycopy(pathBytes, 0, message, 0, pathBytes.length);
        System.arraycopy(hash, 0, message, pathBytes.length, hash.length);

        // Step 3: HMAC-SHA512 using base64 decoded secret
        Mac mac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKey = new SecretKeySpec(Base64.getDecoder().decode(apiSecret), "HmacSHA512");
        mac.init(secretKey);
        byte[] signature = mac.doFinal(message);

        // Step 4: Base64 encode the signature
        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * Token information class
     */
    public static class WebSocketToken {
        private final String token;
        private final long expiresAt;

        public WebSocketToken(String token) {
            this.token = token;
            // Token is valid for 15 minutes
            this.expiresAt = System.currentTimeMillis() + (15 * 60 * 1000);
        }

        public String getToken() {
            return token;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}