package com.interview.challenge.kraken.init;

import com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit;
import com.interview.challenge.kraken.service.KrakenMonitoringService;
import com.interview.challenge.shared.client.UserServiceClient;
import com.interview.challenge.shared.model.ClientConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Initializes Kraken monitoring for existing users on startup
 */
@Slf4j
@Component
public class KrakenUserInitializer {

    @Autowired
    private KrakenMonitoringService monitoringService;

    @Autowired
    private UserServiceClient userServiceClient;

    /**
     * Load existing Kraken users from database when application starts
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeKrakenUsers() {
        log.info("🚀 Initializing Kraken monitoring for existing users...");

        try {
            // Get all users from User Service
            List<ClientConfiguration> allUsers = userServiceClient.getAllClients();

            if (allUsers == null || allUsers.isEmpty()) {
                log.info("No existing users found");
                return;
            }

            int krakenUserCount = 0;

            for (ClientConfiguration user : allUsers) {
                // Only process Kraken users
                if (!"KRAKEN".equalsIgnoreCase(user.getExchange())) {
                    continue;
                }

                String clientId = user.getClientId();

                // Skip if already monitoring
                if (monitoringService.isMonitoring(clientId)) {
                    log.debug("Already monitoring user: {}", clientId);
                    continue;
                }

                try {
                    log.info("🦑 Loading Kraken user: {}", clientId);

                    // Get decrypted credentials
                    Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);

                    if (credentials == null || credentials.get("apiKey") == null) {
                        log.error("Failed to get credentials for user: {}", clientId);
                        continue;
                    }

                    String apiKey = credentials.get("apiKey");
                    String apiSecret = credentials.get("apiSecret");

                    // Convert risk limits
                    RiskLimit dailyRisk = convertRiskLimit(user.getDailyRisk());
                    RiskLimit maxRisk = convertRiskLimit(user.getMaxRisk());

                    // Start monitoring
                    monitoringService.startMonitoring(
                        clientId,
                        apiKey,
                        apiSecret,
                        user.getInitialBalance(),
                        dailyRisk,
                        maxRisk
                    );

                    krakenUserCount++;
                    log.info("✅ Started monitoring for Kraken user: {}", clientId);

                } catch (Exception e) {
                    log.error("Failed to initialize monitoring for user {}: {}", clientId, e.getMessage());
                }
            }

            log.info("✨ Kraken monitoring initialization complete. Loaded {} users", krakenUserCount);

        } catch (Exception e) {
            log.error("Failed to initialize Kraken users: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert risk limit from shared model to internal format
     */
    private RiskLimit convertRiskLimit(com.interview.challenge.shared.model.RiskLimit sharedLimit) {
        if (sharedLimit == null) {
            return null;
        }
        return RiskLimit.builder()
            .type(sharedLimit.getType())
            .value(sharedLimit.getValue())
            .build();
    }
}