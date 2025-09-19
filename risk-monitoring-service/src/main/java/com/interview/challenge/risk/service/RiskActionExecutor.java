package com.interview.challenge.risk.service;

import com.interview.challenge.risk.constants.RiskConstants;
import com.interview.challenge.risk.enums.ViolationType;
import com.interview.challenge.risk.model.AccountMonitoring;
import com.interview.challenge.risk.model.RiskStatus;
import com.interview.challenge.risk.repository.AccountMonitoringRepository;
import com.interview.challenge.risk.websocket.RiskWebSocketHandler;
import com.interview.challenge.shared.audit.MandatoryAuditLogger;
import com.interview.challenge.shared.client.ArchitectAuthInterceptor;
import com.interview.challenge.shared.client.PositionServiceClient;
import com.interview.challenge.shared.client.UserServiceClient;
import com.interview.challenge.shared.dto.ClosePositionsResult;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.model.ClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class RiskActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RiskActionExecutor.class);

    @Autowired
    private AccountMonitoringRepository accountMonitoringRepository;

    @Autowired
    private PositionServiceClient positionServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private MandatoryAuditLogger mandatoryAuditLogger;

    @Autowired
    private RiskWebSocketHandler webSocketHandler;

    @Autowired
    private NotificationPublisher notificationPublisher;

    @Value("${kraken.service.url:http://kraken-service:8086}")
    private String krakenServiceUrl;

    private final WebClient webClient;

    @Autowired
    public RiskActionExecutor(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Execute risk actions based on violation type
     */
    public void executeRiskActions(AccountMonitoring monitoring, ViolationType violationType,
                                   BigDecimal lossAmount, BigDecimal threshold) {
        String clientId = monitoring.getClientId();

        try {
            logger.info("Executing {} actions for client {}", violationType.getDescription(), clientId);

            if (violationType == ViolationType.MAX_RISK) {
                executeMaxRiskActions(monitoring, lossAmount, threshold);
            } else if (violationType == ViolationType.DAILY_RISK) {
                executeDailyRiskActions(monitoring, lossAmount, threshold);
            }

        } catch (Exception e) {
            logger.error("Error executing risk actions for client {}: {}", clientId, e.getMessage());
            notificationPublisher.publishMonitoringError(clientId,
                "Failed to execute " + violationType.getDescription() + " actions: " + e.getMessage());
        }
    }

    /**
     * Execute max risk violation actions
     */
    private void executeMaxRiskActions(AccountMonitoring monitoring, BigDecimal loss, BigDecimal limit) {
        String clientId = monitoring.getClientId();
        logger.error("🚨 EXECUTING MAX RISK ACTIONS for client {}", clientId);

        // Close all positions
        ClosePositionsResult closeResult = closeAllPositions(clientId, RiskConstants.VIOLATION_REASON_MAX_RISK);
        logPositionClosureResult(clientId, closeResult, "MAX RISK");

        // Block account permanently
        String blockReason = String.format(RiskConstants.MAX_RISK_MESSAGE_FORMAT, loss, limit);
        monitoring.blockPermanently(blockReason);
        monitoring.setRiskStatus(RiskStatus.MAX_RISK_TRIGGERED);
        accountMonitoringRepository.save(monitoring);

        // Send notifications
        notificationPublisher.publishMaxRiskTriggered(clientId, loss, limit);

        // Broadcast critical alert
        broadcastRiskAlert(clientId, RiskConstants.ALERT_LEVEL_CRITICAL,
            RiskConstants.MAX_RISK_PERMANENT_BLOCK_MSG,
            createAlertDetails(ViolationType.MAX_RISK, loss, limit, RiskConstants.ACTION_PERMANENT_BLOCK));

        // Log mandatory audit
        mandatoryAuditLogger.logMaxRiskTriggered(clientId, loss, limit, RiskConstants.ACTION_CLOSE_ALL_POSITIONS);

        logger.error("🚨 MAX RISK ACTIONS COMPLETED for client {}: Account permanently blocked", clientId);
    }

    /**
     * Execute daily risk violation actions
     */
    private void executeDailyRiskActions(AccountMonitoring monitoring, BigDecimal loss, BigDecimal limit) {
        String clientId = monitoring.getClientId();
        logger.warn("⚠️ EXECUTING DAILY RISK ACTIONS for client {}", clientId);

        // Close all positions
        ClosePositionsResult closeResult = closeAllPositions(clientId, RiskConstants.VIOLATION_REASON_DAILY_RISK);
        logPositionClosureResult(clientId, closeResult, "DAILY RISK");

        // Block account for the day
        String blockReason = String.format(RiskConstants.DAILY_RISK_MESSAGE_FORMAT, loss, limit);
        monitoring.blockDaily(blockReason);
        monitoring.setRiskStatus(RiskStatus.DAILY_RISK_TRIGGERED);
        accountMonitoringRepository.save(monitoring);

        // Send notifications
        notificationPublisher.publishDailyRiskTriggered(clientId, loss, limit);

        // Broadcast warning alert
        broadcastRiskAlert(clientId, RiskConstants.ALERT_LEVEL_WARNING,
            RiskConstants.DAILY_RISK_DAILY_BLOCK_MSG,
            createAlertDetails(ViolationType.DAILY_RISK, loss, limit, RiskConstants.ACTION_DAILY_BLOCK));

        // Log mandatory audit
        mandatoryAuditLogger.logDailyRiskTriggered(clientId, loss, limit, RiskConstants.ACTION_CLOSE_ALL_POSITIONS);

        logger.warn("⚠️ DAILY RISK ACTIONS COMPLETED for client {}: Trading blocked until tomorrow", clientId);
    }

    /**
     * Close all positions for a client (supports both Architect and Kraken)
     */
    private ClosePositionsResult closeAllPositions(String clientId, String reason) {
        try {
            logger.info("📊 Closing all positions for client {}. Reason: {}", clientId, reason);

            // Get client configuration to determine exchange
            ClientConfiguration clientConfig = userServiceClient.getClientConfiguration(clientId);
            if (clientConfig == null) {
                logger.error("❌ Unable to get client configuration for {}", clientId);
                return createFailedCloseResult("Client configuration not found");
            }

            // Get client credentials from User Service
            Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);
            if (credentials == null || !credentials.containsKey("apiKey") || !credentials.containsKey("apiSecret")) {
                logger.error("❌ Unable to get API credentials for client {}", clientId);
                return createFailedCloseResult("Unable to retrieve API credentials");
            }

            String exchange = clientConfig.getExchange() != null ? clientConfig.getExchange() : "ARCHITECT";

            if ("KRAKEN".equalsIgnoreCase(exchange)) {
                // Close positions via Kraken service
                return closeKrakenPositions(clientId, credentials.get("apiKey"), credentials.get("apiSecret"), reason);
            } else {
                // Close positions via Architect (existing logic)
                return closeArchitectPositions(clientId, credentials.get("apiKey"), credentials.get("apiSecret"), reason);
            }

        } catch (Exception e) {
            logger.error("❌ Error closing positions for client {}: {}", clientId, e.getMessage());
            return createFailedCloseResult(e.getMessage());
        }
    }

    /**
     * Close all positions on Architect exchange
     */
    private ClosePositionsResult closeArchitectPositions(String clientId, String apiKey, String apiSecret, String reason) {
        logger.info("🏛️ Closing Architect positions for client {}", clientId);

        // Set credentials in ThreadLocal for the interceptor
        ArchitectAuthInterceptor.CREDENTIALS.set(
            new ArchitectAuthInterceptor.ApiCredentials(apiKey, apiSecret)
        );

        try {
            ClosePositionsResult result = positionServiceClient.closeAllPositions(clientId, reason);

            if (!result.isSuccess()) {
                logger.warn("⚠️ Failed to close Architect positions for client {}: {}", clientId, result.getMessage());
            } else {
                logger.info("✅ Architect position closure completed for client {}: {} positions closed",
                    clientId, result.getPositionsClosed());
            }

            return result;
        } finally {
            // Clean up ThreadLocal
            ArchitectAuthInterceptor.CREDENTIALS.remove();
        }
    }

    /**
     * Close all positions on Kraken exchange
     */
    private ClosePositionsResult closeKrakenPositions(String clientId, String apiKey, String apiSecret, String reason) {
        logger.info("🦑 Closing Kraken positions for client {}", clientId);

        try {
            String url = krakenServiceUrl + "/api/kraken/positions/close-all/" + clientId;

            Map<String, Object> response = webClient.post()
                    .uri(url)
                    .header("X-API-Key", apiKey)
                    .header("X-API-Secret", apiSecret)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                ClosePositionsResult result = new ClosePositionsResult();
                result.setSuccess(true);
                result.setPositionsClosed((Integer) response.getOrDefault("positionsClosed", 0));
                result.setTotalValue(BigDecimal.valueOf((Double) response.getOrDefault("totalValue", 0.0)));
                result.setMessage("Kraken positions closed: " + reason);

                logger.info("✅ Kraken position closure completed for client {}: {} positions closed",
                    clientId, result.getPositionsClosed());

                return result;
            } else {
                String errorMsg = response != null ?
                    (String) response.getOrDefault("error", "Unknown error") : "No response";
                logger.error("❌ Failed to close Kraken positions: {}", errorMsg);
                return createFailedCloseResult("Kraken closure failed: " + errorMsg);
            }

        } catch (Exception e) {
            logger.error("❌ Error closing Kraken positions for client {}: {}", clientId, e.getMessage());
            return createFailedCloseResult("Kraken API error: " + e.getMessage());
        }
    }

    /**
     * Create failed close positions result
     */
    private ClosePositionsResult createFailedCloseResult(String errorMessage) {
        ClosePositionsResult failedResult = new ClosePositionsResult();
        failedResult.setSuccess(false);
        failedResult.setPositionsClosed(0);
        failedResult.setMessage("Position service error: " + errorMessage);
        return failedResult;
    }

    /**
     * Log position closure result
     */
    private void logPositionClosureResult(String clientId, ClosePositionsResult result, String riskType) {
        if (result.isSuccess()) {
            logger.info("{} position closure for client {}: {} positions closed",
                riskType, clientId, result.getPositionsClosed());
        } else {
            logger.error("{} position closure failed for client {}: {}",
                riskType, clientId, result.getMessage());
        }
    }

    /**
     * Broadcast risk alert via WebSocket
     */
    private void broadcastRiskAlert(String clientId, String alertLevel, String message, Map<String, Object> details) {
        try {
            webSocketHandler.broadcastRiskAlert(clientId, alertLevel, message, details);
        } catch (Exception e) {
            logger.error("Error broadcasting risk alert for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Create alert details map
     */
    private Map<String, Object> createAlertDetails(ViolationType type, BigDecimal loss,
                                                   BigDecimal limit, String action) {
        if (type == ViolationType.MAX_RISK) {
            return Map.of(
                "type", type.getCode(),
                "loss", loss,
                "limit", limit,
                "action", action
            );
        } else {
            return Map.of(
                "type", type.getCode(),
                "dailyLoss", loss,
                "limit", limit,
                "action", action
            );
        }
    }
}