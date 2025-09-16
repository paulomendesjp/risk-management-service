package com.interview.challenge.risk.config;

import com.interview.challenge.risk.service.RiskMonitoringService;
import com.interview.challenge.shared.client.UserServiceClient;
import com.interview.challenge.shared.model.ClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Monitoring Initializer
 *
 * Automatically starts WebSocket monitoring for ALL existing users on service startup
 * This ensures that risk monitoring is active for all registered clients immediately
 */
@Component
public class MonitoringInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringInitializer.class);

    @Autowired
    private RiskMonitoringService riskMonitoringService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserServiceClient userServiceClient;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("===========================================");
        logger.info("🚀 STARTING AUTOMATIC WEBSOCKET MONITORING");
        logger.info("===========================================");

        try {
            // Wait a bit for all services to be ready
            Thread.sleep(5000);

            // Fetch all existing users from MongoDB
            List<ClientConfiguration> allClients = mongoTemplate.findAll(ClientConfiguration.class);

            if (allClients.isEmpty()) {
                logger.info("📭 No clients found in database. Waiting for new registrations...");
                return;
            }

            logger.info("📊 Found {} clients in database. Starting monitoring for all...", allClients.size());

            // Start monitoring for each client asynchronously
            for (ClientConfiguration client : allClients) {
                try {
                    String clientId = client.getClientId();

                    // Skip if client is permanently blocked
                    if (client.isPermanentBlocked()) {
                        logger.info("⛔ Skipping permanently blocked client: {}", clientId);
                        continue;
                    }

                    logger.info("🔄 Starting WebSocket monitoring for client: {}", clientId);

                    // Initialize monitoring data if not exists
                    CompletableFuture.runAsync(() -> {
                        try {
                            // Initialize monitoring in database
                            riskMonitoringService.initializeMonitoring(clientId, client.getInitialBalance());

                            // Get decrypted credentials from user service
                            try {
                                var credentials = userServiceClient.getDecryptedCredentials(clientId);
                                String apiKey = credentials.get("apiKey");
                                String apiSecret = credentials.get("apiSecret");

                                // Start real-time WebSocket monitoring via Python bridge with credentials
                                boolean started = riskMonitoringService.startRealTimeMonitoring(clientId, apiKey, apiSecret);

                                if (started) {
                                    logger.info("✅ WebSocket monitoring started successfully for client: {}", clientId);
                                } else {
                                    logger.warn("⚠️ Failed to start WebSocket monitoring for client: {}", clientId);
                                }
                            } catch (Exception e) {
                                logger.warn("⚠️ Could not get credentials for client {}, skipping WebSocket: {}", clientId, e.getMessage());
                            }
                        } catch (Exception e) {
                            logger.error("❌ Error starting monitoring for client {}: {}", clientId, e.getMessage());
                        }
                    });

                    // Small delay between clients to avoid overwhelming the system
                    Thread.sleep(500);

                } catch (Exception e) {
                    logger.error("❌ Error processing client {}: {}", client.getClientId(), e.getMessage());
                }
            }

            logger.info("===========================================");
            logger.info("✅ MONITORING INITIALIZATION COMPLETED");
            logger.info("📡 Active monitoring for {} clients",
                       allClients.stream().filter(c -> !c.isPermanentBlocked()).count());
            logger.info("===========================================");

        } catch (Exception e) {
            logger.error("❌ Fatal error during monitoring initialization: {}", e.getMessage(), e);
        }
    }
}