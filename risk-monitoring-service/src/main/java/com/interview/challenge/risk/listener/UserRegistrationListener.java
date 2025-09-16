package com.interview.challenge.risk.listener;

import com.interview.challenge.risk.service.RiskMonitoringService;
import com.interview.challenge.shared.client.UserServiceClient;
import com.interview.challenge.shared.event.UserRegistrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * User Registration Event Listener
 *
 * Listens for new user registration events from RabbitMQ
 * and automatically starts WebSocket monitoring for them
 */
@Component
public class UserRegistrationListener {

    private static final Logger logger = LoggerFactory.getLogger(UserRegistrationListener.class);

    @Autowired
    private RiskMonitoringService riskMonitoringService;

    @Autowired
    private UserServiceClient userServiceClient;

    public UserRegistrationListener() {
        logger.info("🎧 UserRegistrationListener initialized - Ready to receive user registration events");
    }

    /**
     * Handle new user registration event
     * Automatically starts WebSocket monitoring for the new user
     */
    @RabbitListener(queues = "user.registrations")
    public void handleUserRegistration(UserRegistrationEvent event) {
        String clientId = event.getClientId();

        logger.info("📬 Received new user registration event for client: {}", clientId);

        CompletableFuture.runAsync(() -> {
            try {
                // Initialize monitoring data
                logger.info("📊 Initializing monitoring for new client: {}", clientId);
                riskMonitoringService.initializeMonitoring(clientId, event.getInitialBalance());

                // Small delay to ensure user data is fully persisted
                Thread.sleep(2000);

                // Fetch decrypted credentials from user service
                logger.info("🔑 Fetching credentials for new client: {}", clientId);
                Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);
                String apiKey = credentials.get("apiKey");
                String apiSecret = credentials.get("apiSecret");

                // Start real-time WebSocket monitoring with credentials
                logger.info("🚀 Starting WebSocket monitoring for new client: {} with credentials", clientId);
                boolean started = riskMonitoringService.startRealTimeMonitoring(clientId, apiKey, apiSecret);

                if (started) {
                    logger.info("✅ WebSocket monitoring activated for new client: {}", clientId);
                } else {
                    logger.error("❌ Failed to start WebSocket monitoring for new client: {}", clientId);
                }

            } catch (Exception e) {
                logger.error("❌ Error handling registration for client {}: {}", clientId, e.getMessage(), e);
            }
        });
    }

    /**
     * Handle user update event (e.g., risk limits changed)
     */
    @RabbitListener(queues = "user.updates")
    public void handleUserUpdate(UserRegistrationEvent event) {
        String clientId = event.getClientId();

        logger.info("🔄 Received user update event for client: {}", clientId);

        try {
            // Force a balance and risk check with new limits
            riskMonitoringService.forceBalanceUpdate(clientId);
            logger.info("✅ Risk limits updated and checked for client: {}", clientId);

        } catch (Exception e) {
            logger.error("❌ Error handling update for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle user deletion event
     */
    @RabbitListener(queues = "user.deletions")
    public void handleUserDeletion(UserRegistrationEvent event) {
        String clientId = event.getClientId();

        logger.info("🗑️ Received user deletion event for client: {}", clientId);

        try {
            // Stop WebSocket monitoring
            boolean stopped = riskMonitoringService.stopRealTimeMonitoring(clientId);

            if (stopped) {
                logger.info("✅ WebSocket monitoring stopped for deleted client: {}", clientId);
            }

        } catch (Exception e) {
            logger.error("❌ Error handling deletion for client {}: {}", clientId, e.getMessage());
        }
    }
}