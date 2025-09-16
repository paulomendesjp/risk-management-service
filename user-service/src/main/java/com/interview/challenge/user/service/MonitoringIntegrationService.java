package com.interview.challenge.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service for integrating with Python Bridge monitoring
 */
@Service
public class MonitoringIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringIntegrationService.class);

    @Value("${architect.bridge.endpoint:http://localhost:8090}")
    private String architectBridgeEndpoint;

    @Autowired
    private UserService userService;
    
    /**
     * Start monitoring for a client in Python Bridge
     */
    public void startMonitoringForClient(String clientId) {
        try {
            logger.info("🚀 Starting automatic monitoring for client: {}", clientId);
            
            // Get decrypted credentials
            Map<String, String> credentials = userService.getDecryptedCredentials(clientId);
            
            // Call Python Bridge to start monitoring
            String pythonBridgeUrl = architectBridgeEndpoint + "/start-monitoring/" + clientId;
            
            // Use RestTemplate to call Python Bridge
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", credentials.get("apiKey"));
            headers.set("api-secret", credentials.get("apiSecret"));
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                pythonBridgeUrl, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ Successfully started monitoring for client: {}", clientId);
            } else {
                logger.error("❌ Failed to start monitoring for client {}: {}", clientId, response.getBody());
            }
            
        } catch (Exception e) {
            logger.error("❌ Error starting monitoring for client {}: {}", clientId, e.getMessage());
            // Don't throw exception - registration should still succeed even if monitoring fails
        }
    }
    
    /**
     * Stop monitoring for a client in Python Bridge
     */
    public void stopMonitoringForClient(String clientId) {
        try {
            logger.info("🛑 Stopping monitoring for client: {}", clientId);
            
            // Call Python Bridge to stop monitoring
            String pythonBridgeUrl = architectBridgeEndpoint + "/stop-monitoring/" + clientId;
            
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                pythonBridgeUrl, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ Successfully stopped monitoring for client: {}", clientId);
            } else {
                logger.error("❌ Failed to stop monitoring for client {}: {}", clientId, response.getBody());
            }
            
        } catch (Exception e) {
            logger.error("❌ Error stopping monitoring for client {}: {}", clientId, e.getMessage());
        }
    }
}

