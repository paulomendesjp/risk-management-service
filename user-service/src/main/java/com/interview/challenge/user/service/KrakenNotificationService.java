package com.interview.challenge.user.service;

import com.interview.challenge.shared.model.ClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to notify Kraken-Service about new user registrations via HTTP
 */
@Service
public class KrakenNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(KrakenNotificationService.class);

    @Value("${kraken.service.url:http://kraken-service:8086}")
    private String krakenServiceUrl;

    @Autowired
    private CredentialManager credentialManager;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Notify Kraken-Service about a new user registration
     */
    public void notifyKrakenUserRegistration(ClientConfiguration user) {
        if (!"KRAKEN".equalsIgnoreCase(user.getExchange())) {
            logger.debug("User {} is not a Kraken user (exchange: {}), skipping notification",
                user.getClientId(), user.getExchange());
            return;
        }

        try {
            logger.info("🦑 Notifying Kraken-Service about new user: {}", user.getClientId());

            // Decrypt credentials before sending
            Map<String, String> decryptedCredentials = credentialManager.decryptCredentials(
                user.getApiKey(), user.getApiSecret());

            // Prepare the request data
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("clientId", user.getClientId());
            requestData.put("apiKey", decryptedCredentials.get("apiKey"));
            requestData.put("apiSecret", decryptedCredentials.get("apiSecret"));
            requestData.put("initialBalance", user.getInitialBalance());

            // Add risk limits if present
            if (user.getDailyRisk() != null) {
                Map<String, Object> dailyRisk = new HashMap<>();
                dailyRisk.put("type", user.getDailyRisk().getType());
                dailyRisk.put("value", user.getDailyRisk().getValue());
                requestData.put("dailyRisk", dailyRisk);
            }

            if (user.getMaxRisk() != null) {
                Map<String, Object> maxRisk = new HashMap<>();
                maxRisk.put("type", user.getMaxRisk().getType());
                maxRisk.put("value", user.getMaxRisk().getValue());
                requestData.put("maxRisk", maxRisk);
            }

            // Create HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create the request
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

            // Call Kraken-Service
            String url = krakenServiceUrl + "/api/kraken/users/register";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("✅ Successfully notified Kraken-Service about user: {}", user.getClientId());
            } else {
                logger.warn("⚠️ Kraken-Service returned status: {} for user: {}",
                    response.getStatusCode(), user.getClientId());
            }

        } catch (Exception e) {
            logger.error("❌ Failed to notify Kraken-Service about user {}: {}",
                user.getClientId(), e.getMessage(), e);
            // Don't throw - this shouldn't break user registration
        }
    }

    /**
     * Notify Kraken-Service about user update
     */
    public void notifyKrakenUserUpdate(ClientConfiguration user) {
        if (!"KRAKEN".equalsIgnoreCase(user.getExchange())) {
            return;
        }

        try {
            logger.info("🔄 Notifying Kraken-Service about user update: {}", user.getClientId());

            // For updates, we might send only the changed fields
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("clientId", user.getClientId());
            requestData.put("action", "update");

            // Add updated risk limits
            if (user.getDailyRisk() != null) {
                Map<String, Object> dailyRisk = new HashMap<>();
                dailyRisk.put("type", user.getDailyRisk().getType());
                dailyRisk.put("value", user.getDailyRisk().getValue());
                requestData.put("dailyRisk", dailyRisk);
            }

            if (user.getMaxRisk() != null) {
                Map<String, Object> maxRisk = new HashMap<>();
                maxRisk.put("type", user.getMaxRisk().getType());
                maxRisk.put("value", user.getMaxRisk().getValue());
                requestData.put("maxRisk", maxRisk);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);

            String url = krakenServiceUrl + "/api/kraken/users/update";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("✅ Successfully notified Kraken-Service about update for user: {}", user.getClientId());
            }

        } catch (Exception e) {
            logger.error("❌ Failed to notify Kraken-Service about update for user {}: {}",
                user.getClientId(), e.getMessage());
        }
    }
}