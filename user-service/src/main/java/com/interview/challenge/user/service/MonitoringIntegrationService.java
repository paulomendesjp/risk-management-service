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
     * Start monitoring for a client
     * Note: Monitoring is now automatically handled by risk-monitoring-service via WebSocket
     * This method is kept for compatibility but doesn't need to call architect-bridge anymore
     */
    public void startMonitoringForClient(String clientId) {
        try {
            logger.info("🚀 Monitoring will be automatically started by risk-monitoring-service for client: {}", clientId);

            // The risk-monitoring-service will automatically detect new clients
            // and start WebSocket connections for balance and position monitoring
            // No need to call architect-bridge /start-monitoring endpoint anymore

            // Just verify that credentials exist
            Map<String, String> credentials = userService.getDecryptedCredentials(clientId);
            if (credentials != null && credentials.get("apiKey") != null) {
                logger.info("✅ Client {} has valid credentials for monitoring", clientId);
            } else {
                logger.warn("⚠️ Client {} missing API credentials for monitoring", clientId);
            }

        } catch (Exception e) {
            logger.error("❌ Error verifying monitoring setup for client {}: {}", clientId, e.getMessage());
            // Don't throw exception - registration should still succeed even if monitoring check fails
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



