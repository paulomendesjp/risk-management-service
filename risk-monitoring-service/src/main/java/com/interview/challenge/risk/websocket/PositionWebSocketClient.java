package com.interview.challenge.risk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.challenge.risk.model.AccountMonitoring;
import com.interview.challenge.risk.repository.AccountMonitoringRepository;
import com.interview.challenge.risk.service.RiskActionExecutor;
import com.interview.challenge.risk.service.RiskMonitoringService;
import com.interview.challenge.shared.client.UserServiceClient;
import com.interview.challenge.shared.model.ClientConfiguration;
import com.interview.challenge.shared.model.RiskLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket client for real-time position and P&L monitoring
 * Monitors unrealized P&L every 2 seconds and triggers risk actions BEFORE limits are breached
 */
@Component
public class PositionWebSocketClient extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(PositionWebSocketClient.class);

    @Value("${architect.bridge.endpoint:http://localhost:8090}")
    private String architectBridgeEndpoint;

    @Autowired
    private RiskMonitoringService riskMonitoringService;

    @Autowired
    private RiskActionExecutor riskActionExecutor;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private AccountMonitoringRepository accountMonitoringRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> positionSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToClientId = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(1);

    // Alert thresholds
    private static final double WARNING_THRESHOLD = 0.8;  // 80% of limit
    private static final double CRITICAL_THRESHOLD = 0.9; // 90% of limit
    private static final double MAX_THRESHOLD = 1.0;      // 100% of limit

    /**
     * Initialize WebSocket connections for position monitoring
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void initializePositionWebSockets() {
        logger.info("📊 Initializing Position WebSocket connections for P&L monitoring");

        // Wait for services to be ready
        try {
            Thread.sleep(10000); // Wait 10 seconds to avoid conflict with OrderFlow initialization
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Connect for all monitored clients
        connectAllPositionWebSockets();

        // Schedule periodic reconnection check
        reconnectExecutor.scheduleAtFixedRate(this::checkAndReconnect, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Connect position WebSocket for all monitored clients
     */
    private void connectAllPositionWebSockets() {
        try {
            logger.info("📊 Connecting Position WebSockets for P&L monitoring");

            // Get all monitored accounts
            List<AccountMonitoring> monitoredAccounts = accountMonitoringRepository.findAll();

            for (AccountMonitoring account : monitoredAccounts) {
                String clientId = account.getClientId();

                // Only connect if not already connected
                if (!positionSessions.containsKey(clientId) || !positionSessions.get(clientId).isOpen()) {
                    logger.info("💹 Connecting Position WebSocket for client: {}", clientId);
                    connectPositionWebSocket(clientId);
                }
            }

            logger.info("✅ Position WebSocket connections initialized");

        } catch (Exception e) {
            logger.error("❌ Failed to initialize Position WebSockets: {}", e.getMessage());
        }
    }

    /**
     * Connect position WebSocket for a specific client
     */
    public void connectPositionWebSocket(String clientId) {
        try {
            // Get client configuration
            Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);
            if (credentials == null || credentials.get("apiKey") == null) {
                logger.warn("⚠️ No configuration found for client {}, skipping position WebSocket", clientId);
                return;
            }

            // Build WebSocket URL for realtime endpoint (positions endpoint doesn't exist)
            String wsUrl = architectBridgeEndpoint.replace("http://", "ws://")
                                                 .replace("https://", "wss://");
            wsUrl = wsUrl + "/ws/realtime?api_key=" + credentials.get("apiKey")
                         + "&api_secret=" + credentials.get("apiSecret");

            logger.info("💹 Connecting Position monitoring via realtime WebSocket for {}", clientId);

            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketSession session = client.doHandshake(this, wsUrl).get(5, TimeUnit.SECONDS);

            if (session.isOpen()) {
                positionSessions.put(clientId, session);
                sessionToClientId.put(session.getId(), clientId);
                logger.info("✅ Position WebSocket connected for {} (session: {})", clientId, session.getId());
            }

        } catch (Exception e) {
            logger.error("❌ Failed to connect Position WebSocket for {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle incoming position updates with P&L data
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            String type = (String) data.get("type");
            String clientId = sessionToClientId.get(session.getId());

            if ("CONNECTION".equals(type)) {
                logger.info("✅ Position WebSocket connected for client {}", clientId);
            }
            else if ("POSITION_UPDATE".equals(type)) {
                handlePositionUpdate(data, clientId);
            }
            else if ("ERROR".equals(type)) {
                logger.error("❌ Position WebSocket error for {}: {}", clientId, data.get("message"));
            }

        } catch (Exception e) {
            logger.error("❌ Error handling position message: {}", e.getMessage());
        }
    }

    /**
     * Handle position updates and check P&L against risk limits
     */
    private void handlePositionUpdate(Map<String, Object> positionData, String clientId) {
        try {
            Double totalUnrealizedPnl = (Double) positionData.get("totalUnrealizedPnl");
            Double accountBalance = (Double) positionData.get("accountBalance");
            List<Map<String, Object>> positions = (List<Map<String, Object>>) positionData.get("positions");

            if (totalUnrealizedPnl == null) {
                return;
            }

            BigDecimal unrealizedPnl = BigDecimal.valueOf(totalUnrealizedPnl);

            // Only process if there's a loss
            if (unrealizedPnl.compareTo(BigDecimal.ZERO) >= 0) {
                logger.debug("📊 {} has profit of ${}, no risk action needed", clientId, unrealizedPnl);
                return;
            }

            // Get client configuration for risk limits
            ClientConfiguration config = userServiceClient.getClientConfiguration(clientId);
            if (config == null) {
                logger.warn("⚠️ No configuration found for client {}", clientId);
                return;
            }

            // Get account monitoring data
            AccountMonitoring monitoring = accountMonitoringRepository.findByClientId(clientId)
                .orElse(null);
            if (monitoring == null) {
                logger.warn("⚠️ No monitoring data found for client {}", clientId);
                return;
            }

            // Calculate risk thresholds
            BigDecimal dailyRiskLimit = calculateRiskLimit(config.getDailyRisk(), monitoring);
            BigDecimal maxRiskLimit = calculateRiskLimit(config.getMaxRisk(), monitoring);

            // Use the more restrictive limit
            BigDecimal activeLimit = dailyRiskLimit.min(maxRiskLimit);
            BigDecimal currentLoss = unrealizedPnl.abs();

            // Calculate risk percentage
            double riskPercentage = currentLoss.divide(activeLimit, 4, RoundingMode.HALF_UP)
                                               .doubleValue();

            logger.info("📊 P&L Monitor for {}: Unrealized Loss: ${} | Limit: ${} | Risk: {}%",
                       clientId, currentLoss, activeLimit, String.format("%.1f", riskPercentage * 100));

            // Check thresholds and take action
            if (riskPercentage >= MAX_THRESHOLD) {
                handleMaxThresholdBreach(clientId, currentLoss, activeLimit, positions);
            }
            else if (riskPercentage >= CRITICAL_THRESHOLD) {
                handleCriticalThreshold(clientId, currentLoss, activeLimit, riskPercentage);
            }
            else if (riskPercentage >= WARNING_THRESHOLD) {
                handleWarningThreshold(clientId, currentLoss, activeLimit, riskPercentage);
            }

            // Update monitoring data with latest P&L
            monitoring.setUnrealizedPnl(unrealizedPnl);
            monitoring.setUpdatedAt(LocalDateTime.now());
            accountMonitoringRepository.save(monitoring);

        } catch (Exception e) {
            logger.error("❌ Error processing position update for {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle WARNING threshold (80% of limit)
     */
    private void handleWarningThreshold(String clientId, BigDecimal loss, BigDecimal limit, double percentage) {
        if (shouldSendAlert(clientId, "WARNING", 60)) { // Alert every 60 seconds max
            logger.warn("⚠️ WARNING: {} approaching risk limit - Loss: ${} / Limit: ${} ({}%)",
                       clientId, loss, limit, String.format("%.1f", percentage * 100));

            // Send warning notification
            sendRiskAlert(clientId, "WARNING", loss, limit, percentage);
        }
    }

    /**
     * Handle CRITICAL threshold (90% of limit)
     */
    private void handleCriticalThreshold(String clientId, BigDecimal loss, BigDecimal limit, double percentage) {
        if (shouldSendAlert(clientId, "CRITICAL", 30)) { // Alert every 30 seconds max
            logger.error("🚨 CRITICAL: {} very close to risk limit - Loss: ${} / Limit: ${} ({}%)",
                        clientId, loss, limit, String.format("%.1f", percentage * 100));

            // Send critical alert
            sendRiskAlert(clientId, "CRITICAL", loss, limit, percentage);

            // Prepare for position closure
            logger.warn("🚨 Preparing to close positions if loss increases for {}", clientId);
        }
    }

    /**
     * Handle MAX threshold breach (100% of limit) - CLOSE ALL POSITIONS
     */
    private void handleMaxThresholdBreach(String clientId, BigDecimal loss, BigDecimal limit,
                                         List<Map<String, Object>> positions) {
        logger.error("💥 RISK LIMIT BREACHED for {} - Loss: ${} exceeds Limit: ${}",
                    clientId, loss, limit);

        // Log position details
        if (positions != null && !positions.isEmpty()) {
            logger.error("📊 Open positions causing breach:");
            for (Map<String, Object> pos : positions) {
                logger.error("  - Symbol: {} | Qty: {} | P&L: ${}",
                           pos.get("symbol"), pos.get("quantity"), pos.get("unrealizedPnl"));
            }
        }

        // Trigger immediate risk action
        triggerEmergencyRiskAction(clientId, loss, limit);
    }

    /**
     * Calculate risk limit based on configuration
     */
    private BigDecimal calculateRiskLimit(RiskLimit riskLimit, AccountMonitoring monitoring) {
        if (riskLimit == null) {
            return BigDecimal.valueOf(1000000); // Very high default to avoid false triggers
        }

        if (riskLimit.isAbsolute()) {
            return riskLimit.getValue();
        } else {
            // Percentage based on initial balance
            BigDecimal base = monitoring.getInitialBalance() != null
                ? monitoring.getInitialBalance()
                : monitoring.getCurrentBalance();
            return base.multiply(riskLimit.getValue())
                      .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Check if we should send an alert (rate limiting)
     */
    private boolean shouldSendAlert(String clientId, String level, int intervalSeconds) {
        String key = clientId + "_" + level;
        LocalDateTime lastAlert = lastAlertTime.get(key);
        LocalDateTime now = LocalDateTime.now();

        if (lastAlert == null || lastAlert.plusSeconds(intervalSeconds).isBefore(now)) {
            lastAlertTime.put(key, now);
            return true;
        }
        return false;
    }

    /**
     * Send risk alert notification
     */
    private void sendRiskAlert(String clientId, String level, BigDecimal loss, BigDecimal limit, double percentage) {
        // This would publish to notification service
        logger.info("📢 Sending {} alert for {} - {}% of limit reached", level, clientId,
                   String.format("%.1f", percentage * 100));
    }

    /**
     * Trigger emergency risk action (close all positions)
     */
    @Async
    private void triggerEmergencyRiskAction(String clientId, BigDecimal loss, BigDecimal limit) {
        try {
            logger.error("🚨🚨🚨 EMERGENCY RISK ACTION for {} - Closing all positions NOW!", clientId);

            // Get account monitoring
            AccountMonitoring monitoring = accountMonitoringRepository.findByClientId(clientId)
                .orElse(null);

            if (monitoring != null) {
                // Determine if it's daily or max risk violation
                ClientConfiguration config = userServiceClient.getClientConfiguration(clientId);
                boolean isDailyRisk = loss.compareTo(config.getDailyRisk().getValue()) <= 0;

                if (isDailyRisk) {
                    logger.error("📍 Daily risk limit breached - Executing daily risk actions");
                    riskActionExecutor.executeDailyRiskActions(monitoring, loss, limit);
                } else {
                    logger.error("📍 MAX risk limit breached - Executing max risk actions");
                    riskActionExecutor.executeMaxRiskActions(monitoring, loss, limit);
                }
            }

        } catch (Exception e) {
            logger.error("❌ Failed to execute emergency risk action for {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle WebSocket connection established
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("✅ Position WebSocket connection established: {}", session.getId());
    }

    /**
     * Handle WebSocket connection closed
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientId = sessionToClientId.get(session.getId());
        logger.warn("📊 Position WebSocket closed for {}: {}", clientId, status.toString());

        // Clean up
        if (clientId != null) {
            positionSessions.remove(clientId);
            sessionToClientId.remove(session.getId());

            // Schedule reconnection
            reconnectExecutor.schedule(() -> {
                logger.info("🔄 Reconnecting Position WebSocket for {}", clientId);
                connectPositionWebSocket(clientId);
            }, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Handle WebSocket errors
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("❌ Position WebSocket error: {}", exception.getMessage());
    }

    /**
     * Check and reconnect disconnected sessions
     */
    private void checkAndReconnect() {
        positionSessions.forEach((clientId, session) -> {
            if (!session.isOpen()) {
                logger.warn("🔄 Position WebSocket for {} disconnected, reconnecting...", clientId);
                connectPositionWebSocket(clientId);
            }
        });
    }

    /**
     * Add position monitoring for a new client
     */
    public void addPositionMonitoring(String clientId) {
        logger.info("➕ Adding Position P&L monitoring for: {}", clientId);
        connectPositionWebSocket(clientId);
    }

    /**
     * Remove position monitoring for a client
     */
    public void removePositionMonitoring(String clientId) {
        logger.info("➖ Removing Position P&L monitoring for: {}", clientId);

        WebSocketSession session = positionSessions.get(clientId);
        if (session != null && session.isOpen()) {
            try {
                session.close();
                positionSessions.remove(clientId);
                sessionToClientId.remove(session.getId());
            } catch (Exception e) {
                logger.error("❌ Error closing Position WebSocket for {}: {}", clientId, e.getMessage());
            }
        }
    }
}