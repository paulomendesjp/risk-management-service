package com.interview.challenge.risk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.challenge.risk.constants.RiskConstants;
import com.interview.challenge.risk.enums.WebSocketMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🐍 PYTHON BRIDGE WEBSOCKET HANDLER
 *
 * Handles WebSocket connections FROM Python Bridge TO Java Risk Service
 * This enables real-time communication between Python and Java
 */
@Component
public class PythonBridgeWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(PythonBridgeWebSocketHandler.class);

    @Autowired
    private ApplicationContext applicationContext;

    private RiskWebSocketHandler riskWebSocketHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);

        logger.info("🐍 Python Bridge WebSocket connected: {}", sessionId);
        logger.info("📊 Total active Python connections: {}", activeSessions.size());

        // Send welcome message
        sendMessage(session, Map.of(
            "type", RiskConstants.MSG_TYPE_CONNECTION_ESTABLISHED,
            "sessionId", sessionId,
            "message", "Java Risk Service WebSocket ready"
        ));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            String payload = message.getPayload().toString();
            logger.debug("📨 Received message from Python Bridge: {}", payload);

            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);

            String messageTypeStr = (String) messageData.get("type");

            try {
                WebSocketMessageType messageType = WebSocketMessageType.fromType(messageTypeStr);

                switch (messageType) {
                    case BALANCE_UPDATE:
                        handleBalanceUpdate(messageData);
                        break;

                    case RISK_VIOLATION:
                        handleRiskViolation(messageData);
                        break;

                    case MONITORING_ERROR:
                        handleMonitoringError(messageData);
                        break;

                    case HEARTBEAT:
                        handleHeartbeat(session, messageData);
                        break;

                    default:
                        logger.warn("⚠️ Unknown message type from Python Bridge: {}", messageTypeStr);
                        sendMessage(session, Map.of(
                            "type", RiskConstants.MSG_TYPE_ERROR,
                            "message", "Unknown message type: " + messageTypeStr
                        ));
                }
            } catch (IllegalArgumentException e) {
                logger.warn("⚠️ Invalid message type from Python Bridge: {}", messageTypeStr);
                sendMessage(session, Map.of(
                    "type", RiskConstants.MSG_TYPE_ERROR,
                    "message", "Invalid message type: " + messageTypeStr
                ));
            }

        } catch (Exception e) {
            logger.error("❌ Error processing message from Python Bridge: {}", e.getMessage(), e);
            sendMessage(session, Map.of(
                "type", RiskConstants.MSG_TYPE_ERROR,
                "message", "Error processing message: " + e.getMessage()
            ));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("🚨 WebSocket transport error with Python Bridge {}: {}",
                    session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);

        logger.info("🔌 Python Bridge WebSocket disconnected: {} ({})", sessionId, closeStatus);
        logger.info("📊 Remaining Python connections: {}", activeSessions.size());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 💰 HANDLE BALANCE UPDATE FROM PYTHON
     */
    private void handleBalanceUpdate(Map<String, Object> messageData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> balanceData = (Map<String, Object>) messageData.get("data");

            String clientId = (String) balanceData.get("client_id");
            Object balanceObj = balanceData.get("balance");
            String source = (String) balanceData.get("source");

            logger.info("💰 Processing real-time balance update from Python: client={}, balance=${}, source={}",
                       clientId, balanceObj, source);

            // Process the balance update (this would call your existing service logic)
            // riskMonitoringService.processBalanceUpdate(balanceData);

            // Broadcast to frontend clients immediately
            getRiskWebSocketHandler().broadcastBalanceUpdate(clientId, balanceData);

            logger.info("✅ Balance update processed and broadcasted");

        } catch (Exception e) {
            logger.error("❌ Error handling balance update: {}", e.getMessage(), e);
        }
    }

    /**
     * 🚨 HANDLE RISK VIOLATION FROM PYTHON
     */
    private void handleRiskViolation(Map<String, Object> messageData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> riskData = (Map<String, Object>) messageData.get("data");

            String clientId = (String) riskData.get("client_id");
            String violationType = (String) riskData.get("violation_type");

            logger.warn("🚨 Risk violation detected by Python: client={}, type={}", clientId, violationType);

            // Broadcast risk alert immediately
            getRiskWebSocketHandler().broadcastRiskAlert(clientId, riskData);

            logger.info("✅ Risk violation alert broadcasted");

        } catch (Exception e) {
            logger.error("❌ Error handling risk violation: {}", e.getMessage(), e);
        }
    }

    /**
     * 🚨 HANDLE MONITORING ERROR FROM PYTHON
     */
    private void handleMonitoringError(Map<String, Object> messageData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorData = (Map<String, Object>) messageData.get("data");

            String clientId = (String) errorData.get("client_id");
            String error = (String) errorData.get("error");

            logger.error("🚨 Monitoring error from Python: client={}, error={}", clientId, error);

            // Broadcast system notification
            getRiskWebSocketHandler().broadcastSystemNotification(
                "Monitoring error for client " + clientId + ": " + error,
                "error"
            );

        } catch (Exception e) {
            logger.error("❌ Error handling monitoring error: {}", e.getMessage(), e);
        }
    }

    /**
     * 💓 HANDLE HEARTBEAT FROM PYTHON
     */
    private void handleHeartbeat(WebSocketSession session, Map<String, Object> messageData) {
        try {
            logger.debug("💓 Heartbeat from Python Bridge");

            // Send heartbeat response
            sendMessage(session, Map.of(
                "type", RiskConstants.MSG_TYPE_HEARTBEAT_RESPONSE,
                "timestamp", System.currentTimeMillis(),
                "status", RiskConstants.STATUS_HEALTHY
            ));

        } catch (Exception e) {
            logger.error("❌ Error handling heartbeat: {}", e.getMessage(), e);
        }
    }

    /**
     * 📤 SEND MESSAGE TO PYTHON BRIDGE
     */
    public void sendMessageToPython(Map<String, Object> message) {
        for (WebSocketSession session : activeSessions.values()) {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    /**
     * 📤 SEND MESSAGE TO SPECIFIC SESSION
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            logger.error("❌ Error sending message to Python Bridge: {}", e.getMessage());
        }
    }

    /**
     * 📊 GET CONNECTION STATUS
     */
    public Map<String, Object> getConnectionStatus() {
        return Map.of(
            "activePythonConnections", activeSessions.size(),
            "sessions", activeSessions.keySet()
        );
    }

    /**
     * Get RiskWebSocketHandler lazily to avoid circular dependency
     */
    private RiskWebSocketHandler getRiskWebSocketHandler() {
        if (riskWebSocketHandler == null) {
            riskWebSocketHandler = applicationContext.getBean(RiskWebSocketHandler.class);
        }
        return riskWebSocketHandler;
    }
}