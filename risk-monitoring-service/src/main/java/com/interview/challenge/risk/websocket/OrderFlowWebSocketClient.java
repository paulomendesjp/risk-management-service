package com.interview.challenge.risk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.challenge.risk.model.AccountMonitoring;
import com.interview.challenge.risk.repository.AccountMonitoringRepository;
import com.interview.challenge.risk.service.RiskMonitoringService;
import com.interview.challenge.shared.client.UserServiceClient;
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
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket client for real-time orderflow streaming from Architect Bridge
 * Enables <2 second risk detection instead of 30 second polling
 */
@Component
public class OrderFlowWebSocketClient extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderFlowWebSocketClient.class);

    @Value("${architect.bridge.endpoint:http://localhost:8090}")
    private String architectBridgeEndpoint;

    @Autowired
    private RiskMonitoringService riskMonitoringService;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private AccountMonitoringRepository accountMonitoringRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToClientId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(1);

    /**
     * Connect to orderflow WebSocket for each monitored client
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void initializeWebSocketConnections() {
        logger.info("🚀 Initializing WebSocket connections for real-time orderflow monitoring");

        // Wait a bit for services to be ready
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Connect WebSocket for each monitored client
        connectAllMonitoredClients();

        // Schedule periodic reconnection check
        reconnectExecutor.scheduleAtFixedRate(this::checkAndReconnect, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Connect WebSocket for all monitored clients
     */
    private void connectAllMonitoredClients() {
        try {
            logger.info("📡 Connecting WebSocket clients for orderflow monitoring");

            // Get all monitored clients from MongoDB
            List<AccountMonitoring> monitoredAccounts = accountMonitoringRepository.findAll();

            logger.info("📊 Found {} monitored accounts in database", monitoredAccounts.size());

            for (AccountMonitoring account : monitoredAccounts) {
                String clientId = account.getClientId();

                // Only connect if not already connected
                if (!clientSessions.containsKey(clientId) || !clientSessions.get(clientId).isOpen()) {
                    logger.info("🔄 Connecting WebSocket for client: {}", clientId);
                    connectClientWebSocket(clientId);
                } else {
                    logger.debug("✅ WebSocket already connected for client: {}", clientId);
                }
            }

            logger.info("✅ WebSocket connection attempts completed for {} clients", monitoredAccounts.size());

        } catch (Exception e) {
            logger.error("❌ Failed to initialize WebSocket connections: {}", e.getMessage());
        }
    }

    /**
     * Connect WebSocket for a specific client
     */
    public void connectClientWebSocket(String clientId) {
        try {
            // Get client configuration
            Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);
            if (credentials == null || credentials.get("apiKey") == null) {
                logger.warn("⚠️ No configuration found for client {}, skipping WebSocket connection", clientId);
                return;
            }

            // Build WebSocket URL
            String wsUrl = architectBridgeEndpoint.replace("http://", "ws://")
                                                 .replace("https://", "wss://");
            // Use realtime endpoint (orderflow endpoint doesn't exist)
            wsUrl = wsUrl + "/ws/realtime?api_key=" + credentials.get("apiKey")
                         + "&api_secret=" + credentials.get("apiSecret");

            logger.info("📡 Connecting OrderFlow monitoring via realtime WebSocket for client {}", clientId);

            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketSession session = client.doHandshake(this, wsUrl).get(5, TimeUnit.SECONDS);

            if (session.isOpen()) {
                clientSessions.put(clientId, session);
                sessionToClientId.put(session.getId(), clientId);
                logger.info("✅ WebSocket connected for client {} (session: {})", clientId, session.getId());
            }

        } catch (Exception e) {
            logger.error("❌ Failed to connect WebSocket for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle incoming WebSocket messages (orderflow updates)
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            String type = (String) data.get("type");
            String event = (String) data.get("event");

            // Get clientId from session mapping
            String clientId = sessionToClientId.get(session.getId());

            // Handle different message types
            if ("CONNECTION".equals(type)) {
                logger.info("✅ WebSocket connection confirmed for client {}: {}", clientId, data.get("status"));
            }
            else if ("ORDER_FILL".equals(event)) {
                handleOrderFill(data, clientId);
            }
            else if ("POSITION_UPDATE".equals(type)) {
                handlePositionUpdate(data, clientId);
            }
            else if ("ERROR".equals(type)) {
                logger.error("❌ WebSocket error for client {}: {}", clientId, data.get("message"));
            }

        } catch (Exception e) {
            logger.error("❌ Error handling WebSocket message: {}", e.getMessage());
        }
    }

    /**
     * Handle order fill events - CRITICAL for immediate risk checks
     */
    private void handleOrderFill(Map<String, Object> fillData, String clientId) {
        try {
            String symbol = (String) fillData.get("symbol");
            Double quantity = (Double) fillData.get("quantity");
            Double price = (Double) fillData.get("price");
            String side = (String) fillData.get("side");

            logger.info("🎯 ORDER FILL DETECTED for {} - Symbol: {} | Qty: {} | Price: {} | Side: {}",
                       clientId, symbol, quantity, price, side);

            // Calculate approximate loss/gain
            BigDecimal fillValue = BigDecimal.valueOf(quantity * price);
            if ("SELL".equals(side) || "SHORT".equals(side)) {
                fillValue = fillValue.negate();
            }

            logger.info("🚨 IMMEDIATE RISK CHECK for client {} after fill of ${}",
                       clientId, fillValue.abs());

            // Trigger immediate risk check
            triggerImmediateRiskCheck(clientId, fillValue);

        } catch (Exception e) {
            logger.error("❌ Error processing order fill for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle position updates with unrealized P&L
     */
    private void handlePositionUpdate(Map<String, Object> positionData, String clientId) {
        try {
            Double totalUnrealizedPnl = (Double) positionData.get("totalUnrealizedPnl");
            Double accountBalance = (Double) positionData.get("accountBalance");
            Map<String, Object> riskAlert = (Map<String, Object>) positionData.get("riskAlert");

            if (riskAlert != null) {
                String alertType = (String) riskAlert.get("type");
                Double unrealizedLoss = (Double) riskAlert.get("unrealizedLoss");
                Double threshold = (Double) riskAlert.get("threshold");

                logger.warn("⚠️ RISK ALERT for {}: {} | Unrealized Loss: ${} | Threshold: ${}",
                           clientId, alertType, unrealizedLoss, threshold);

                // Trigger risk action if needed
                if (clientId != null && unrealizedLoss != null) {
                    triggerRiskAction(clientId, BigDecimal.valueOf(unrealizedLoss));
                }
            }

            // Log position update
            logger.debug("📊 Position Update for {} - Unrealized P&L: ${} | Balance: ${}",
                        clientId, totalUnrealizedPnl, accountBalance);

        } catch (Exception e) {
            logger.error("❌ Error processing position update for {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Trigger immediate risk check after order fill
     */
    @Async
    private void triggerImmediateRiskCheck(String clientId, BigDecimal fillValue) {
        try {
            logger.info("🔍 Performing immediate risk check for client {} after fill", clientId);

            // Get decrypted credentials
            Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);
            if (credentials != null) {
                // Trigger balance update and risk check immediately
                riskMonitoringService.updateBalanceAndCheckRisk(
                    clientId,
                    credentials.get("apiKey"),
                    credentials.get("apiSecret")
                ).get(5, TimeUnit.SECONDS);

                logger.info("✅ Immediate risk check completed for client {}", clientId);
            }

        } catch (Exception e) {
            logger.error("❌ Failed to perform immediate risk check for client {}: {}",
                        clientId, e.getMessage());
        }
    }

    /**
     * Trigger risk action for critical alerts
     */
    private void triggerRiskAction(String clientId, BigDecimal unrealizedLoss) {
        logger.warn("🚨 Triggering risk action for client {} with unrealized loss: ${}",
                   clientId, unrealizedLoss.abs());

        // This would trigger position closure and account blocking
        // Implementation depends on your risk management policies
    }


    /**
     * Handle WebSocket connection opened
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("✅ WebSocket connection established: {}", session.getId());
    }

    /**
     * Handle WebSocket connection closed
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientId = sessionToClientId.get(session.getId());
        logger.warn("📡 WebSocket connection closed for client {}: {} | Status: {}",
                   clientId, session.getId(), status.toString());

        // Clean up mappings
        if (clientId != null) {
            clientSessions.remove(clientId);
            sessionToClientId.remove(session.getId());

            // Schedule reconnection for this specific client
            reconnectExecutor.schedule(() -> {
                logger.info("🔄 Attempting to reconnect WebSocket for client {}", clientId);
                connectClientWebSocket(clientId);
            }, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Handle WebSocket errors
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("❌ WebSocket transport error: {}", exception.getMessage());
    }

    /**
     * Check and reconnect disconnected sessions
     */
    private void checkAndReconnect() {
        clientSessions.forEach((clientId, session) -> {
            if (!session.isOpen()) {
                logger.warn("🔄 WebSocket for client {} disconnected, reconnecting...", clientId);
                connectClientWebSocket(clientId);
            }
        });
    }

    /**
     * Add new client to WebSocket monitoring
     * Called when a new client is registered
     */
    public void addClientMonitoring(String clientId) {
        logger.info("➕ Adding WebSocket monitoring for new client: {}", clientId);

        // Check if already connected
        if (clientSessions.containsKey(clientId) && clientSessions.get(clientId).isOpen()) {
            logger.info("✅ WebSocket already active for client: {}", clientId);
            return;
        }

        // Connect WebSocket for new client
        connectClientWebSocket(clientId);
    }

    /**
     * Remove client from WebSocket monitoring
     * Called when a client is removed or deactivated
     */
    public void removeClientMonitoring(String clientId) {
        logger.info("➖ Removing WebSocket monitoring for client: {}", clientId);

        WebSocketSession session = clientSessions.get(clientId);
        if (session != null && session.isOpen()) {
            try {
                session.close();
                clientSessions.remove(clientId);
                sessionToClientId.remove(session.getId());
                logger.info("✅ WebSocket closed for client: {}", clientId);
            } catch (Exception e) {
                logger.error("❌ Error closing WebSocket for client {}: {}", clientId, e.getMessage());
            }
        }
    }

    /**
     * Get status of WebSocket connections
     */
    public Map<String, Boolean> getConnectionStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        clientSessions.forEach((clientId, session) -> {
            status.put(clientId, session.isOpen());
        });
        return status;
    }
}