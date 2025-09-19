package com.interview.challenge.risk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.challenge.risk.model.AccountMonitoring;
import com.interview.challenge.risk.repository.AccountMonitoringRepository;
import com.interview.challenge.shared.client.UserServiceClient;
import org.springframework.context.ApplicationEventPublisher;
import com.interview.challenge.shared.event.BalanceUpdateEvent;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket client for real-time balance streaming from Architect Bridge
 * Enables <5 second balance detection instead of 30 second polling
 *
 * Features:
 * - Real-time balance updates via WebSocket streaming
 * - Automatic reconnection on disconnect
 * - Immediate risk checks on balance changes
 * - Per-client WebSocket connections
 */
@Component
public class BalanceWebSocketClient extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(BalanceWebSocketClient.class);

    @Value("${architect.bridge.endpoint:http://localhost:8090}")
    private String architectBridgeEndpoint;

    @Value("${architect.bridge.use-realtime:true}")
    private boolean useRealtimeEndpoint;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private AccountMonitoringRepository accountMonitoringRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToClientId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(2);

    /**
     * Connect to balance WebSocket for each monitored client
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void initializeWebSocketConnections() {
        logger.info("🚀 Initializing WebSocket connections for real-time balance monitoring");

        // Wait a bit for services to be ready
        try {
            Thread.sleep(8000);  // Wait for architect-bridge to be fully ready
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Connect WebSocket for each monitored client
        connectAllMonitoredClients();

        // Schedule periodic reconnection check
        reconnectExecutor.scheduleAtFixedRate(this::checkAndReconnect, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Connect WebSocket for all monitored clients
     */
    private void connectAllMonitoredClients() {
        try {
            logger.info("💰 Connecting WebSocket clients for balance monitoring");

            // Get all monitored clients from MongoDB
            List<AccountMonitoring> monitoredAccounts = accountMonitoringRepository.findAll();

            logger.info("📊 Found {} monitored accounts in database", monitoredAccounts.size());

            for (AccountMonitoring account : monitoredAccounts) {
                String clientId = account.getClientId();

                // Only connect if not already connected
                if (!clientSessions.containsKey(clientId) || !clientSessions.get(clientId).isOpen()) {
                    logger.info("🔄 Connecting balance WebSocket for client: {}", clientId);
                    connectClientWebSocket(clientId);
                } else {
                    logger.debug("✅ Balance WebSocket already connected for client: {}", clientId);
                }
            }

            logger.info("✅ Balance WebSocket connection attempts completed for {} clients", monitoredAccounts.size());

        } catch (Exception e) {
            logger.error("❌ Failed to initialize balance WebSocket connections: {}", e.getMessage());
        }
    }

    /**
     * Connect WebSocket for a specific client
     */
    public void connectClientWebSocket(String clientId) {
        try {
            // Get decrypted credentials
            Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);
            if (credentials == null || credentials.get("apiKey") == null) {
                logger.warn("⚠️ No credentials found for client {}, skipping balance WebSocket connection", clientId);
                return;
            }

            String apiKey = credentials.get("apiKey");
            String apiSecret = credentials.get("apiSecret");

            // Build WebSocket URL for balance streaming
            String wsUrl = architectBridgeEndpoint.replace("http://", "ws://")
                                                 .replace("https://", "wss://");

            // Use real-time endpoint if enabled (default: true)
            String endpoint = useRealtimeEndpoint ? "/ws/realtime" : "/ws/balance";
            wsUrl = wsUrl + endpoint + "?api_key=" + apiKey
                         + "&api_secret=" + apiSecret;

            logger.info("🔗 WebSocket connecting for clientId: {}", clientId);
            logger.info("   🌐 Endpoint: {}", wsUrl.split("\\?")[0]);
            logger.info("   🔑 API Key: {}...{}",
                       apiKey.substring(0, Math.min(10, apiKey.length())),
                       apiKey.substring(Math.max(0, apiKey.length() - 4)));

            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketSession session = client.doHandshake(this, wsUrl).get(10, TimeUnit.SECONDS);

            if (session.isOpen()) {
                clientSessions.put(clientId, session);
                sessionToClientId.put(session.getId(), clientId);
                logger.info("✅ WebSocket CONNECTED - Client mapping established:");
                logger.info("   👤 ClientId: {}", clientId);
                logger.info("   🌍 SessionId: {}", session.getId());
                logger.info("   🔑 API Key: {}...{}",
                           apiKey.substring(0, Math.min(10, apiKey.length())),
                           apiKey.substring(Math.max(0, apiKey.length() - 4)));
                logger.info("   📡 Status: ACTIVE");
            }

        } catch (Exception e) {
            logger.error("❌ Failed to connect balance WebSocket for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle incoming WebSocket messages (balance updates)
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            String type = (String) data.get("type");
            String clientId = sessionToClientId.get(session.getId());

            logger.debug("📨 Message received - clientId: {} | type: {} | session: {}",
                        clientId, type, session.getId());

            // Handle different message types
            if ("CONNECTION".equals(type)) {
                String accountId = (String) data.get("accountId");
                String apiKeyPrefix = (String) data.get("apiKeyPrefix");
                logger.info("✅ WebSocket CONNECTION CONFIRMED:");
                logger.info("   👤 ClientId (local): {}", clientId);
                logger.info("   🎯 Account ID (Architect): {}", accountId);
                logger.info("   🔑 API Key: {}", apiKeyPrefix);
                logger.info("   📡 Status: {}", data.get("status"));
                logger.info("   💬 Message: {}", data.get("message"));
            }
            else if ("BALANCE_UPDATE".equals(type)) {
                String source = (String) data.get("source");
                if (source != null) {
                    logger.debug("📊 Balance update from {}: client={}", source, clientId);
                }
                handleBalanceUpdate(data, clientId);
            }
            else if ("PNL_UPDATE".equals(type)) {
                handlePnlUpdate(data, clientId);
            }
            else if ("ERROR".equals(type)) {
                logger.error("❌ WebSocket error for client {}: {}", clientId, data.get("message"));
            }

        } catch (Exception e) {
            logger.error("❌ Error handling balance WebSocket message: {}", e.getMessage());
        }
    }

    /**
     * Handle P&L update events from position monitoring
     */
    @SuppressWarnings("unchecked")
    private void handlePnlUpdate(Map<String, Object> pnlData, String clientId) {
        try {
            Double totalUnrealizedPnl = (Double) pnlData.get("totalUnrealizedPnl");
            Double totalBalance = (Double) pnlData.get("totalBalance");
            List<Map<String, Object>> positions = (List<Map<String, Object>>) pnlData.get("positions");

            logger.info("💹 P&L UPDATE for client {}: Total Unrealized P&L: ${}, Balance: ${}",
                       clientId,
                       totalUnrealizedPnl != null ? totalUnrealizedPnl : 0,
                       totalBalance != null ? totalBalance : "N/A");

            if (positions != null && !positions.isEmpty()) {
                logger.debug("📊 Open positions for {}: {}", clientId, positions.size());
                for (Map<String, Object> pos : positions) {
                    String symbol = (String) pos.get("symbol");
                    Double pnl = (Double) pos.get("unrealizedPnl");
                    if (Math.abs(pnl) > 10) { // Log significant P&L
                        logger.info("📈 Position P&L for {} - {}: ${}", clientId, symbol, pnl);
                    }
                }
            }

            // If total balance provided, trigger balance update
            if (totalBalance != null) {
                Map<String, Object> balanceUpdate = new HashMap<>();
                balanceUpdate.putAll(pnlData);
                balanceUpdate.put("unrealizedPnl", totalUnrealizedPnl);
                balanceUpdate.put("source", "pnl_monitor");
                handleBalanceUpdate(balanceUpdate, clientId);
            }

        } catch (Exception e) {
            logger.error("❌ Error processing P&L update for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle balance update events - CRITICAL for immediate risk checks
     */
    private void handleBalanceUpdate(Map<String, Object> balanceData, String clientId) {
        try {
            Double totalBalance = (Double) balanceData.get("totalBalance");
            Double previousBalance = (Double) balanceData.get("previousBalance");
            String timestamp = (String) balanceData.get("timestamp");

            logger.info("💰 BALANCE UPDATE DETECTED:");
            logger.info("   👤 ClientId: {}", clientId);
            logger.info("   💵 New Balance: ${}", totalBalance);
            logger.info("   💴 Previous: ${}", previousBalance);
            logger.info("   📈 Change: ${}", previousBalance != null ? (totalBalance - previousBalance) : "N/A");
            logger.info("   🕰️ Timestamp: {}", timestamp);

            if (totalBalance != null) {
                // Create balance update event
                BalanceUpdateEvent balanceUpdate = new BalanceUpdateEvent();
                balanceUpdate.setClientId(clientId);
                balanceUpdate.setNewBalance(BigDecimal.valueOf(totalBalance));
                balanceUpdate.setPreviousBalance(previousBalance != null ? BigDecimal.valueOf(previousBalance) : null);
                balanceUpdate.setSource("websocket");
                balanceUpdate.setTimestamp(LocalDateTime.now());

                logger.info("🚨 PUBLISHING BALANCE UPDATE EVENT for client {} via WebSocket", clientId);

                // Publish the balance update event - RiskMonitoringService will handle it
                eventPublisher.publishEvent(balanceUpdate);

                logger.info("✅ WebSocket balance update event published for client {}", clientId);
            }

        } catch (Exception e) {
            logger.error("❌ Error processing balance update for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle WebSocket connection opened
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("✅ Balance WebSocket connection established: {}", session.getId());
    }

    /**
     * Handle WebSocket connection closed
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientId = sessionToClientId.get(session.getId());
        logger.warn("💰 Balance WebSocket connection closed for client {}: {} | Status: {}",
                   clientId, session.getId(), status.toString());

        // Clean up mappings
        if (clientId != null) {
            clientSessions.remove(clientId);
            sessionToClientId.remove(session.getId());

            // Schedule reconnection for this specific client
            reconnectExecutor.schedule(() -> {
                logger.info("🔄 Attempting to reconnect balance WebSocket for client {}", clientId);
                connectClientWebSocket(clientId);
            }, 10, TimeUnit.SECONDS);
        }
    }

    /**
     * Handle WebSocket errors
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String clientId = sessionToClientId.get(session.getId());
        logger.error("❌ Balance WebSocket transport error for client {}: {}", clientId, exception.getMessage());
    }

    /**
     * Check and reconnect disconnected sessions
     */
    private void checkAndReconnect() {
        logger.debug("🔍 Checking balance WebSocket connections for reconnection");

        clientSessions.forEach((clientId, session) -> {
            if (!session.isOpen()) {
                logger.warn("🔄 Balance WebSocket for client {} disconnected, reconnecting...", clientId);
                clientSessions.remove(clientId);
                sessionToClientId.remove(session.getId());
                connectClientWebSocket(clientId);
            }
        });
    }

    /**
     * Add new client to balance WebSocket monitoring
     * Called when a new client is registered
     */
    public void addClientMonitoring(String clientId) {
        logger.info("➕ Adding balance WebSocket monitoring for new client: {}", clientId);

        // Check if already connected
        if (clientSessions.containsKey(clientId) && clientSessions.get(clientId).isOpen()) {
            logger.info("✅ Balance WebSocket already active for client: {}", clientId);
            return;
        }

        // Connect WebSocket for new client
        connectClientWebSocket(clientId);
    }

    /**
     * Remove client from balance WebSocket monitoring
     * Called when a client is removed or deactivated
     */
    public void removeClientMonitoring(String clientId) {
        logger.info("➖ Removing balance WebSocket monitoring for client: {}", clientId);

        WebSocketSession session = clientSessions.get(clientId);
        if (session != null && session.isOpen()) {
            try {
                session.close();
                clientSessions.remove(clientId);
                sessionToClientId.remove(session.getId());
                logger.info("✅ Balance WebSocket closed for client: {}", clientId);
            } catch (Exception e) {
                logger.error("❌ Error closing balance WebSocket for client {}: {}", clientId, e.getMessage());
            }
        }
    }

    /**
     * Get status of balance WebSocket connections
     */
    public Map<String, Boolean> getConnectionStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        clientSessions.forEach((clientId, session) -> {
            status.put(clientId, session.isOpen());
        });
        return status;
    }

    /**
     * Get connection statistics
     */
    public Map<String, Object> getConnectionStats() {
        return Map.of(
            "totalConnections", clientSessions.size(),
            "activeConnections", clientSessions.values().stream().mapToInt(s -> s.isOpen() ? 1 : 0).sum(),
            "connectionDetails", getConnectionStatus()
        );
    }
}