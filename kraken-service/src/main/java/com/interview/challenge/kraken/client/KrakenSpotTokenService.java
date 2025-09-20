package com.interview.challenge.kraken.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Service to get WebSocket authentication token from Kraken Spot API
 * Required for authenticated WebSocket connections to monitor balances
 */
@Slf4j
@Service
public class KrakenSpotTokenService {

    private static final String TOKEN_ENDPOINT = "/0/private/GetWebSocketsToken";

    @Value("${kraken.api.spot-url:https://api.kraken.com}")
    private String spotApiUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Get WebSocket authentication token for Spot API
     *
     * @param apiKey The API key (must have WebSocket permission)
     * @param apiSecret The API secret (base64 encoded)
     * @return WebSocket token valid for 15 minutes to establish connection
     */
    public String getWebSocketToken(String apiKey, String apiSecret) {
        try {
            log.info("🎫 Requesting WebSocket token from Kraken Spot API");

            String url = spotApiUrl + TOKEN_ENDPOINT;
            long nonce = System.currentTimeMillis();

            // Prepare POST data
            String postData = "nonce=" + nonce;

            // Generate signature for Spot API
            String signature = generateSpotSignature(apiSecret, TOKEN_ENDPOINT, nonce, postData);

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("API-Key", apiKey);
            headers.set("API-Sign", signature);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Create request
            HttpEntity<String> request = new HttpEntity<>(postData, headers);

            // Make REST call
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );

            // Check response
            if (response.getBody() == null || response.getBody().isEmpty()) {
                log.error("❌ Empty response from Kraken API. Status: {}", response.getStatusCode());
                throw new RuntimeException("Empty response from Kraken API");
            }

            log.debug("📋 Kraken API response: {}", response.getBody());

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
     * Generate signature for Kraken Spot API
     *
     * Spot API signature format:
     * 1. Create message = URI path + SHA256(nonce + POST data)
     * 2. HMAC-SHA512(message) using base64 decoded API secret
     * 3. Base64 encode the result
     */
    private String generateSpotSignature(String apiSecret, String path, long nonce, String postData) {
        try {
            // Step 1: SHA256 hash of (nonce + POST data)
            String nonceAndData = nonce + postData;
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(nonceAndData.getBytes(StandardCharsets.UTF_8));

            // Step 2: Combine path + hash
            byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
            byte[] message = new byte[pathBytes.length + hash.length];
            System.arraycopy(pathBytes, 0, message, 0, pathBytes.length);
            System.arraycopy(hash, 0, message, pathBytes.length, hash.length);

            // Step 3: HMAC-SHA512
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(Base64.getDecoder().decode(apiSecret), "HmacSHA512");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(message);

            // Step 4: Base64 encode
            return Base64.getEncoder().encodeToString(hmac);

        } catch (Exception e) {
            log.error("Failed to generate signature: {}", e.getMessage());
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
}